# N+1 개선 — 수정 내역

게시글 목록 조회(`GET /posts`)의 N+1을 제거하기 위해 연관 데이터(count·좋아요·작성자)를
**반복문 밖에서 배치로 한 번에** 조회하도록 바꿨다.

- Before: 페이지당 SELECT **63~64개** (측정, [`N_PLUS_1_ANALYSIS.md`](N_PLUS_1_ANALYSIS.md), [`nplus1_before.log`](nplus1_before.log))
- After: 페이지당 SELECT **5개** (측정, [`nplus1_after.log`](nplus1_after.log), size와 무관한 상수)

---

## 변경 파일 4개 요약

| 파일 | 변경 |
|------|------|
| `PostService.java` | `getPostList` 배치화 (유저 1회 + count 배치 + 좋아요 배치) |
| `PostRepository.java` | 목록 쿼리에 `join fetch p.user` (작성자 N+1 제거) |
| `LikeRepository.java` | `findLikedPostIds` 배치 조회 쿼리 추가 |
| `LikeService.java` | `likedPostIds(userId, postIds)` 배치 메서드 추가 |

---

## 1. `PostService.getPostList` — 핵심 변경

### Before (N+1)
```java
@Transactional
public CursorPageResponseDto<PostResponseDto> getPostList(Long cursor, int size, String email) {
    ...
    List<PostResponseDto> postContent = posts.stream()
            .map(post -> {
                Count count = this.countRepository.findByPostId(post.getId()).orElse(null); // 게시글마다 쿼리
                boolean isLiked = likeService.checkLike(post.getId(), email);               // 게시글마다 쿼리 2개
                return PostResponseDto.builder()
                        ...
                        .author(post.getUser().getNickname())   // 작성자 지연 로딩
                        ...
            }).collect(Collectors.toList());
    ...
}
```
- 반복문 안에서 `findByPostId`(count), `checkLike`(내부에서 `findByEmail` + `existsById`)를 게시글마다 호출.
- `checkLike`의 `findByEmail`은 **매번 같은 로그인 유저**를 다시 조회하는 낭비.

### After (배치)
```java
public CursorPageResponseDto<PostResponseDto> getPostList(Long cursor, int size, String email) {
    ...
    // === N+1 제거: 연관 데이터를 반복문 밖에서 배치로 한 번에 조회 ===
    // 로그인 유저 1회 조회 (기존: 게시글마다 checkLike 안에서 findByEmail 반복)
    User me = this.userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

    List<Long> postIds = posts.stream().map(Post::getId).toList();

    // count 배치 조회 → postId 기준 Map (Count는 @MapsId라 getId()가 곧 postId)
    Map<Long, Count> countMap = this.countRepository.findByPostIn(posts).stream()
            .collect(Collectors.toMap(Count::getId, count -> count));

    // 내가 좋아요한 postId 집합을 배치로 조회
    Set<Long> likedPostIds = this.likeService.likedPostIds(me.getId(), postIds);

    List<PostResponseDto> postContent = posts.stream()
            .map(post -> {
                Count count = countMap.get(post.getId());          // Map 조회 (쿼리 없음)
                boolean isLiked = likedPostIds.contains(post.getId()); // Set 조회 (쿼리 없음)
                return PostResponseDto.builder()
                        ...
                        .author(post.getUser().getNickname())      // join fetch로 이미 로딩됨
                        ...
            }).collect(Collectors.toList());
    ...
}
```

**변경 포인트**
- **유저 조회를 반복문 밖으로 hoist**: `findByEmail`을 20번 → 1번.
- **count 배치**: `countRepository.findByPostIn(posts)`로 1번에 가져와 `Map<postId, Count>`로 조회.
  - `Count`는 `@OneToOne @MapsId`라 `count.getId()`가 곧 `post_id`이므로 post 연관을 건드리지 않고 키로 사용.
- **좋아요 배치**: `likeService.likedPostIds(me.getId(), postIds)`로 좋아요한 postId 집합을 1번에.
- **메서드 레벨 `@Transactional` 제거**: 클래스 레벨 `@Transactional(readOnly = true)`를 상속받도록(순수 읽기).

---

## 2. `PostRepository` — 작성자 fetch join

### Before
```java
List<Post> findByDeletedAtIsNullOrderByIdDesc(Pageable pageable);
List<Post> findByIdLessThanAndDeletedAtIsNullOrderByIdDesc(Long cursor, Pageable pageable);
```
파생 쿼리라 작성자(`post.getUser()`) 접근 시 별도 SELECT 발생(작성자가 다양하면 N+1).

### After
```java
@Query("select p from Post p join fetch p.user where p.deletedAt is null order by p.id desc")
List<Post> findByDeletedAtIsNullOrderByIdDesc(Pageable pageable);

@Query("select p from Post p join fetch p.user where p.id < :cursor and p.deletedAt is null order by p.id desc")
List<Post> findByIdLessThanAndDeletedAtIsNullOrderByIdDesc(@Param("cursor") Long cursor, Pageable pageable);
```
`join fetch p.user`로 게시글과 작성자를 **한 쿼리에** 로딩 → 작성자 개별 조회 제거.
(단일값 연관 + Pageable 조합은 in-memory 페이징 문제가 없어 안전.)

---

## 3. `LikeRepository` — 배치 조회 쿼리

```java
// 특정 유저가 좋아요한 postId를 목록으로 한 번에 조회 (N+1 제거용 배치)
@Query("select l.id.postId from Like l " +
       "where l.id.userId = :userId and l.id.postId in :postIds and l.deletedAt is null")
List<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
```
`Like`의 복합키(`LikePK{userId, postId}`)를 이용해, 주어진 postId들 중 유저가 좋아요한 것만 반환.
게시글마다 `existsById`를 호출하던 것을 **IN절 1번**으로 대체.

---

## 4. `LikeService` — 배치 메서드

```java
// 목록 조회용 배치: 유저가 좋아요한 postId 집합을 한 번의 쿼리로 조회 (N+1 제거)
@Transactional(readOnly = true)
public Set<Long> likedPostIds(Long userId, List<Long> postIds) {
    if (postIds == null || postIds.isEmpty()) {
        return Set.of();
    }
    return new HashSet<>(this.likeRepository.findLikedPostIds(userId, postIds));
}
```
`PostService`가 기존 `likeService` 의존만으로 배치 조회를 쓰도록 감싼 메서드. 빈 목록은 빈 집합 반환(방어).

---

## Before / After 쿼리 비교 (측정값)

```
Before (size=20, 측정 63):        After (size=20, 측정 5):
  1  게시글 목록                    1  게시글 목록 + 작성자(join fetch)
  21 findByEmail (checkLike20+JWT1)  2  findByEmail (JWT필터1 + getPostList1)
  20 count  (findByPostId)          1  count (findByPostIn, IN절 20개)
  20 like   (existsById)            1  like  (findLikedPostIds, IN절 20개)
  1  작성자 (getUser 지연 로딩)
```

→ **63개 → 5개**. count·like 각 20 → 1(IN절), findByEmail 21 → 2, 작성자는 목록 쿼리에 흡수. 페이지 크기와 무관한 상수.

---

## 실제 실행 쿼리 설명

캡처 로그([`nplus1_before.log`](nplus1_before.log) / [`nplus1_after.log`](nplus1_after.log)) 기준으로, 각 SQL이 무엇이며 왜 그렇게 나갔는지.

### Before — 63개

**① 게시글 목록 (1회)**
```sql
select p.post_id, p.content, p.created_at, p.deleted_at, p.title, p.updated_at, p.user_id
from post p
where p.deleted_at is null order by p.post_id desc limit ?
```
커서 페이징의 목록 쿼리. 작성자(user)는 여기서 안 가져온다 → 이후 개별 로딩 유발.

**② findByEmail (21회)** ← 최대 낭비
```sql
select u.user_id, u.created_at, ..., u.profile_image_url, u.updated_at
from user u where u.email = 'benchuser@test.com'
```
`checkLike`가 게시글마다 `findByEmail`을 호출(20회) + JWT 필터의 인증 로딩(1회). **21번 모두 같은 유저**를 조회하는 완전한 중복.

**③ count 개별 조회 (20회)**
```sql
select c.post_id, c.comment_count, c.like_count, c.view_count
from count c left join post p on p.post_id = c.post_id
where p.post_id = ?
```
게시글마다 count를 1건씩. (`left join post`는 `findByPostId`가 `count.post.id`를 탐색하며 생긴 조인.)

**④ 좋아요 존재 확인 (20회)**
```sql
select l.post_id, l.user_id from `like` l
where (l.post_id, l.user_id) = (?, ?) and l.deleted_at is null limit ?
```
게시글마다 복합키로 좋아요 여부를 1건씩 확인(`existsById`).

**⑤ 작성자 로딩 (1회)**
```sql
select u.user_id, ... from user u where u.user_id = ?
```
`post.getUser()` 접근 시 작성자를 로딩. 20개 글이 모두 user_id=1이라 영속성 컨텍스트 캐시로 1회만.
(작성자가 다양했다면 여기서도 N회 발생 → 잠재적 N+1.)

### After — 5개

**① 게시글 목록 + 작성자 (1회)** ← 작성자 흡수
```sql
select p.post_id, p.content, ..., u.user_id, u.created_at, ..., u.updated_at
from post p join user u on u.user_id = p.user_id
where p.deleted_at is null order by p.post_id desc limit ?
```
`join fetch p.user` 덕분에 게시글과 작성자를 **한 쿼리에** JOIN 로딩. Before의 ①+⑤가 하나로 합쳐졌다.

**② count 배치 (1회)** ← 20 → 1
```sql
select c.post_id, c.comment_count, c.like_count, c.view_count
from count c where c.post_id in (?,?,?, ... 20개)
```
`findByPostIn(posts)`로 20개 count를 IN절 **한 번**에. (Before의 `left join post`도 사라져 더 단순해짐.)

**③ 좋아요 배치 (1회)** ← 20 → 1
```sql
select l.post_id from `like` l
where l.user_id = ? and l.post_id in (?,?,?, ... 20개) and l.deleted_at is null
```
`findLikedPostIds(userId, postIds)`로 좋아요한 postId만 IN절 한 번에. 결과는 `Set`으로 담아 `contains()`로 메모리 조회.

**④ findByEmail (2회)** ← 21 → 2
```sql
select u.user_id, ... from user u where u.email = 'benchuser@test.com'
```
1회는 JWT 필터의 인증 로딩, 1회는 `getPostList`가 좋아요 판단용 유저를 조회.
→ 이 2회는 서로 다른 계층. `SecurityContext`의 principal을 재사용하면 1회로 더 줄일 수 있음(미세 최적화, N+1 아님).

---

## 검증
- `./gradlew compileJava` 통과 (JBR 21).
- After 측정: `./benchmark/capture_queries.sh after 20` → **5개** 확인 ([`nplus1_after.log`](nplus1_after.log)).
- size 무관 상수: `capture_queries.sh after 5`로 재측정 시에도 5개 유지 예상(게시글 수에 비례하지 않음).
