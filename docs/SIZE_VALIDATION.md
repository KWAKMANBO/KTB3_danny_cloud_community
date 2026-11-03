# 목록 조회 `size` 파라미터 검증

게시글/댓글 목록 조회의 `size` 파라미터에 상·하한 검증을 추가하고, `@WebMvcTest`로 검증 동작을 테스트한다.

- 브랜치: `refactor/addchecksize`
- 관련 커밋: `d367024`(검증 코드), `bebbf0c`(spring-security-test 의존성), 테스트 추가 커밋

---

## 1. 문제

목록 조회의 `size`에 상·하한이 없어 클라이언트가 임의 값을 넣을 수 있었다.

- `PostController.getPosts` → `size`(기본 20)
- `PostController.getComment` → `size`(기본 5)

| 요청 | 결과 |
|------|------|
| `?size=0` | `PageRequest.of(0, 1)` — 1건만 조회(의도와 다름) |
| `?size=-5` | `PageRequest`에서 `IllegalArgumentException` → 500 |
| `?size=1000000` | 100만 건 한 번에 조회 시도 → 성능·메모리 위험 |

특히 초대형 `size`는 커서 페이징으로 확보한 성능을 무력화하고 서버 과부하를 유발할 수 있는 공격 표면이다.

---

## 2. 해결 — 선언적 검증 (Bean Validation)

서비스에서 `if`로 막는 대신, 컨트롤러 진입점에서 선언적으로 검증한다.

### 2-1. 컨트롤러에 제약 추가 (`PostController`)
```java
@RestController
@RequestMapping("/posts")
@Validated                       // @RequestParam 검증을 활성화 (필수)
public class PostController {

    @GetMapping()
    public ResponseEntity<...> getPosts(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(20) int size,   // ← 검증
            Authentication authentication) { ... }

    @GetMapping("/{postId}/comments")
    public ResponseEntity<?> getComment(
            @PathVariable Long postId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int size,    // ← 검증
            Authentication authentication) { ... }
}
```

- **`@Validated`(클래스 레벨)가 반드시 필요하다.** 이게 있어야 `@RequestParam`의 `@Min/@Max`가 `MethodValidationPostProcessor`에 의해 실제로 적용된다. 없으면 애노테이션만 있고 검증이 동작하지 않는다.
- 범위를 벗어나면 `ConstraintViolationException`이 던져진다.

### 2-2. 예외 핸들러 추가 (`GlobalExceptionHandler`)
```java
@ExceptionHandler(ConstraintViolationException.class)
public ResponseEntity<ApiResponseDto<?>> handleConstraintViolation(ConstraintViolationException e) {
    String msg = e.getConstraintViolations().stream()
            .map(v -> v.getMessage()).findFirst().orElse("Wrong Request Parameter.");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponseDto.error(msg));
}
```

- **왜 필요한가:** `@RequestParam` 검증 위반은 `ConstraintViolationException`을 던진다. 이는 요청 바디(`@Valid`) 위반인 `MethodArgumentNotValidException`과 **다른 예외**다.
- 핸들러가 없으면 `@ExceptionHandler(Exception.class)` 폴백으로 잡혀 **500**이 난다. 따로 등록해야 **400 Bad Request**로 응답한다.

> 처음엔 서비스에서 `if (size > 20) throw new InvalidSizeException(...)` 방식(옵션 A)으로 시작했으나,
> 검증은 진입점(컨트롤러)에서 선언적으로 하는 것이 표준이라 Bean Validation(옵션 B)으로 전환했다.
> (`InvalidSizeException` 커스텀 예외는 제거)

---

## 3. 테스트 — `@WebMvcTest`

`src/test/java/com/ktb/community/controller/PostControllerTest.java`

```java
@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean PostService postService;
    @MockitoBean CommentService commentService;
    @MockitoBean LikeService likeService;
    @MockitoBean JwtUtil jwtUtil;                       // 보안 필터 빈 생성에 필요
    @MockitoBean UserDetailsService userDetailsService; // 〃

    @Test void size_zero_rejected() throws Exception {      // size=0 → 400
        mockMvc.perform(get("/posts").param("size", "0"))
               .andExpect(status().isBadRequest());
    }

    @Test void size_over_max_rejected() throws Exception {  // size=21 → 400
        mockMvc.perform(get("/posts").param("size", "21"))
               .andExpect(status().isBadRequest());
    }

    @Test void size_valid_ok() throws Exception {           // size=20 → 200
        when(postService.getPostList(any(), eq(20), anyString()))
                .thenReturn(new CursorPageResponseDto<>(List.of(), null, false));
        mockMvc.perform(get("/posts").param("size", "20")
                        .principal(new UsernamePasswordAuthenticationToken("test@example.com", null, List.of())))
               .andExpect(status().isOk());
    }
}
```

### 테스트 설계 포인트와 삽질 기록

| 이슈 | 원인 | 해결 |
|------|------|------|
| `@MockBean` deprecated 경고 | Spring Boot 3.4+에서 deprecated | `@MockitoBean`(`org.springframework.test.context.bean.override.mockito`)로 교체 |
| `NoSuchBeanDefinitionException: JwtUtil` | `@WebMvcTest`가 보안 설정을 로드하며 `JwtAuthenticationFilter`(JwtUtil, UserDetailsService 의존) 빈 생성 시도 | `@MockitoBean`으로 `JwtUtil`·`UserDetailsService` 추가 |
| `NoResourceFoundException: No static resource posts` | `@WebMvcTest(PostControllerTest.class)` 오타 — 테스트 클래스 자신을 지정해 컨트롤러 미로드 | `@WebMvcTest(PostController.class)`로 수정 |
| `Authentication is null` (NPE) | `addFilters=false`로 보안 필터를 꺼서 request에 principal이 안 심김 | `.principal(new UsernamePasswordAuthenticationToken(...))`로 직접 주입 |

### 핵심 개념
- **`addFilters = false`**: 보안 필터 체인을 끈다. `/posts`는 인증 필요(`anyRequest().authenticated()`) 엔드포인트라, 필터를 켜두면 size 검증 전에 401로 막혀 테스트가 불가능하다. 인증은 이 테스트의 관심사가 아니므로 꺼서 검증 로직에 집중한다.
- **`.principal(...)`**: 필터를 끈 부작용으로 `Authentication` 파라미터가 null이 되는데, 인증이 필요한 정상(200) 케이스에서만 가짜 사용자를 요청에 직접 주입해 메서드 본문이 돌게 한다. (400 케이스는 검증에서 먼저 막혀 principal 불필요.)
- **spring-security-test 의존성**: `testImplementation 'org.springframework.security:spring-security-test'` 추가(커밋 `bebbf0c`).

---

## 4. 결과

```
size = 0   → 400 Bad Request
size = 21  → 400 Bad Request
size = 20  → 200 OK
size 없음  → 200 OK (기본 20)
```

- `@WebMvcTest` 3개 테스트 통과, 전체 테스트 스위트 통과.
- 초대형/음수/0 size 요청이 요청 레벨에서 400으로 거부됨.
