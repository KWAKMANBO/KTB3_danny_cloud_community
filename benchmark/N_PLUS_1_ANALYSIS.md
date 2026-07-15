# N+1 발생 지점 분석

게시글 목록 조회에서 페이지당 SELECT **64개**가 측정됐다(`size=20`, [`count_queries.sh`](count_queries.sh)).
그 원인이 되는 코드와, 프로젝트 내 다른 N+1 후보를 정리한다.

## N+1이란
목록을 조회하는 **1번의 쿼리** 이후, 결과 N건 **각각에 대해 추가 쿼리**가 나가는 문제.
`1 + N` (여기서는 연관 데이터가 3종이라 `1 + N×3`) 꼴로 쿼리가 폭증한다.

---

## 🔴 핵심: `PostService.getPostList` (게시글 목록)

파일: `src/main/java/com/ktb/community/service/PostService.java:90-106`

```java
List<PostResponseDto> postContent = posts.stream()
        .map(post -> {                                                    // 게시글 20개 → 20번 반복
            Count count = this.countRepository.findByPostId(post.getId()); // ① 반복마다 쿼리
            boolean isLiked = likeService.checkLike(post.getId(), email);  // ②③ 반복마다 쿼리
            return PostResponseDto.builder()
                    .author(post.getUser().getNickname())                  // ④ 지연 로딩
                    ...
        }).collect(Collectors.toList());
```

반복문(`.map()`) **한 바퀴마다 DB를 조회**하는 것이 원인. 원인별로 나누면:

### ① count 개별 조회 — `PostService.java:92`
```java
Count count = this.countRepository.findByPostId(post.getId());
```
게시글마다 `SELECT * FROM count WHERE post_id = ?` → **20번**.
20개 postId를 모아 `WHERE post_id IN (...)` 한 번으로 가능하나 개별로 나눠 날림.

### ②③ checkLike — `PostService.java:93` → `LikeService.java:88-95`
```java
public boolean checkLike(Long postId, String email) {
    User user = this.userRepository.findByEmail(email)      // ② 매번 유저 재조회 (항상 같은 유저!)
            .orElseThrow(...);
    LikePK pk = new LikePK(user.getId(), postId);
    return this.likeRepository.existsByIdAndDeletedAtIsNull(pk);  // ③ 좋아요 존재 확인
}
```
게시글마다 **2번씩** 쿼리 → **40번**.
- **②** `findByEmail(email)`의 `email`은 20번 모두 **같은 로그인 유저**. 같은 유저를 20번 다시 조회하는 완전한 낭비. (쿼리 메서드라 엔티티 캐시가 안 됨)
- **③** 좋아요 여부를 게시글마다 개별 확인.

### ④ 작성자 지연 로딩 — `PostService.java:98`
```java
.author(post.getUser().getNickname())
```
목록 쿼리(`PostRepository.java:19`)가 `join fetch p.user`를 하지 않아 `post.getUser()` 접근 시 작성자 User를 별도 SELECT로 로딩.
- 이번 벤치는 20개 글이 모두 같은 작성자(user_id=1)라 영속성 컨텍스트 캐시로 **1번만** 발생. (작성자가 제각각이면 여기서도 20번 발생)

### 합계 (size=20, 측정값 64)
```
1  : SELECT ... FROM post ...              (목록 = "1")
──────────── 이후 반복문 안 ────────────
20 : count   findByPostId                  ①
20 : user    findByEmail (같은 유저 반복!)   ②
20 : like    existsById                     ③
1  : user    작성자 로딩                     ④
1  : JWT 필터의 로그인 유저 조회
= 64
```

### 개선 방향 (→ 상수 ~4개)
| 원인 | 개선 |
|------|------|
| ② 유저 조회 | 반복문 **밖에서 1회** `findByEmail` |
| ① count | `countRepository.findByPostIn(posts)` 배치 1회 → Map 조회 |
| ③ 좋아요 | 유저의 좋아요를 postId 목록으로 배치 1회 |
| ④ 작성자 | 목록 쿼리에 `join fetch p.user` 추가 |

→ 64개 → **약 4개**(목록1 + count1 + 좋아요1 + 유저1), size와 무관한 상수.

---

## ✅ 대조군: `CommentService.getCommentList` (댓글 목록) — N+1 없음

파일: `src/main/java/com/ktb/community/service/CommentService.java:46-79`

이미 올바르게 구현되어 있다.
- `user`를 반복문 **밖에서 1회만** 조회 (`CommentService.java:48`)
- 댓글 쿼리가 `select c ... join fetch c.user` (`CommentRepository.java:16,19`)라 `comment.getUser()`가 **미리 로딩됨**
- `.map()` 안에 레포 호출이 **없음**

→ 게시글 목록에 적용하려는 fetch join/배치 패턴이 **댓글에는 이미 적용**되어 있다.
"댓글은 fetch join으로 해결돼 있었고, 동일 패턴을 게시글에도 적용" 이라는 서술의 근거.

---

## 🟡 `PostService.getPostContent` (게시글 상세) — N+1 아님, 중복만

파일: `src/main/java/com/ktb/community/service/PostService.java:113~`

게시글 **1건** 조회라 목록형 N+1은 아니다(쿼리 수 상수). 다만 `checkLike`(`:134`) 내부에서 `findByEmail`을 다시 조회 → 상세 로직에서 이미 유저를 조회했다면 중복. 심각도 낮음.

---

## 🟠 `PostService.deletePost` (게시글 삭제) — 쓰기 경로의 N+1

파일: `src/main/java/com/ktb/community/service/PostService.java:287-288`

```java
List<Comment> comments = this.commentRepository.findByPostId(postId);
comments.forEach(comment -> comment.setDeletedAt(LocalDateTime.now()));
```
댓글을 전부 로딩한 뒤 하나씩 `deletedAt` 설정 → flush 시 **댓글 개수만큼 UPDATE** 발생.
- SELECT가 아니라 **UPDATE 쪽 N+1**.
- 개선: `UPDATE comment SET deleted_at = now() WHERE post_id = ?` 벌크 업데이트 1회.
- 삭제는 핫패스가 아니므로 우선순위 낮음.

---

## 요약

| 위치 | N+1 | 심각도 | 조치 |
|------|-----|--------|------|
| `getPostList` (목록) | 🔴 있음 (64쿼리) | 높음 | 이번 개선 대상 (배치 + fetch join) |
| `getCommentList` (댓글목록) | ✅ 없음 | — | 이미 fetch join (대조군) |
| `getPostContent` (상세) | 🟡 N+1 아님, 중복 조회 | 낮음 | 유저 재사용 |
| `deletePost` (삭제) | 🟠 UPDATE N+1 | 낮음 | 벌크 업데이트 |

**결론:** 읽기 핫패스에서 반드시 고칠 곳은 `getPostList` 하나. 나머지는 보너스 개선.
