package com.ktb.community.service;

import com.ktb.community.dto.request.CreatePostRequestDto;
import com.ktb.community.dto.request.ModifyPostRequestDto;
import com.ktb.community.dto.response.CrudPostResponseDto;
import com.ktb.community.dto.response.CursorPageResponseDto;
import com.ktb.community.dto.response.PostDetailResponseDto;
import com.ktb.community.dto.response.PostResponseDto;
import com.ktb.community.entity.*;
import com.ktb.community.exception.custom.PostNotFoundException;
import com.ktb.community.exception.custom.UnauthorizedException;
import com.ktb.community.jwt.JwtUtil;
import com.ktb.community.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PostService 테스트")
public class PostServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private CountRepository countRepository;
    @Mock
    private ImageRepository imageRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private PostService postService;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Nested
    @DisplayName("게시글 작성 테스트")
    class CreatePostTest {

        @Test
        @DisplayName("이미지 없이 게시글 작성 성공")
        void createPost_WithoutImages_Success() {
            // given
            String email = "test@example.com";
            CreatePostRequestDto requestDto = new CreatePostRequestDto("Test Title", "Test Content", List.of());

            User user = new User();
            user.setId(1L);
            user.setEmail(email);

            Post savedPost = new Post();
            savedPost.setId(1L);
            savedPost.setTitle("Test Title");
            savedPost.setContent("Test Content");
            savedPost.setUser(user);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(postRepository.save(any(Post.class))).thenReturn(savedPost);
            when(countRepository.save(any(Count.class))).thenReturn(new Count());

            // when
            CrudPostResponseDto result = postService.createPost(requestDto, email);

            // then
            assertThat(result.getPostId()).isEqualTo(1L);
            verify(userRepository).findByEmail(email);
            verify(postRepository).save(any(Post.class));
            verify(countRepository).save(any(Count.class));
            verify(imageRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("이미지와 함께 게시글 작성 성공")
        void createPost_WithImages_Success() {
            // given
            String email = "test@example.com";
            CreatePostRequestDto requestDto = new CreatePostRequestDto(
                    "Test Title",
                    "Test Content",
                    Arrays.asList("image1.jpg", "image2.jpg")
            );

            User user = new User();
            user.setId(1L);
            user.setEmail(email);

            Post savedPost = new Post();
            savedPost.setId(1L);
            savedPost.setTitle("Test Title");
            savedPost.setContent("Test Content");
            savedPost.setUser(user);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(postRepository.save(any(Post.class))).thenReturn(savedPost);
            when(countRepository.save(any(Count.class))).thenReturn(new Count());
            when(imageRepository.saveAll(anyList())).thenReturn(List.of());

            // when
            CrudPostResponseDto result = postService.createPost(requestDto, email);

            // then
            assertThat(result.getPostId()).isEqualTo(1L);
            verify(userRepository).findByEmail(email);
            verify(postRepository).save(any(Post.class));
            verify(imageRepository).saveAll(anyList());
            verify(countRepository).save(any(Count.class));
        }

        @Test
        @DisplayName("사용자를 찾을 수 없는 경우 예외 발생")
        void createPost_UserNotFound_ThrowsException() {
            // given
            String email = "notfound@example.com";
            CreatePostRequestDto requestDto = new CreatePostRequestDto("Test Title", "Test Content", List.of());

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> postService.createPost(requestDto, email))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Users not found");

            verify(userRepository).findByEmail(email);
            verify(postRepository, never()).save(any(Post.class));
        }
    }

    @Nested
    @DisplayName("게시글 목록 조회 테스트")
    class GetPostListTest {

        @Test
        @DisplayName("첫 페이지 조회 성공 (cursor = null)")
        void getPostList_FirstPage_Success() {
            // given
            Long cursor = null;
            int size = 2;

            User user = new User();
            user.setId(1L);
            user.setNickname("author1");

            Post post1 = createPost(1L, "Title 1", "Content 1", user);
            Post post2 = createPost(2L, "Title 2", "Content 2", user);
            Post post3 = createPost(3L, "Title 3", "Content 3", user);

            List<Post> posts = Arrays.asList(post1, post2, post3);

            Count count1 = createCount(1L, 10L, 5L, 2L);
            Count count2 = createCount(2L, 20L, 10L, 3L);

            when(postRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(any(Pageable.class)))
                    .thenReturn(posts);
            when(countRepository.findByPostId(1L)).thenReturn(Optional.of(count1));
            when(countRepository.findByPostId(2L)).thenReturn(Optional.of(count2));

            // when
            CursorPageResponseDto<PostResponseDto> result = postService.getPostList(cursor, size);

            // then
            assertThat(result.getPosts()).hasSize(2);
            assertThat(result.getHasNext()).isTrue();
            assertThat(result.getNextCursor()).isEqualTo(2L);
            verify(postRepository).findByDeletedAtIsNullOrderByCreatedAtDesc(any(Pageable.class));
        }

        @Test
        @DisplayName("다음 페이지 조회 성공 (cursor 있음)")
        void getPostList_NextPage_Success() {
            // given
            Long cursor = 10L;
            int size = 2;

            User user = new User();
            user.setId(1L);
            user.setNickname("author1");

            Post post1 = createPost(8L, "Title 8", "Content 8", user);
            Post post2 = createPost(9L, "Title 9", "Content 9", user);

            List<Post> posts = Arrays.asList(post1, post2);

            Count count1 = createCount(8L, 5L, 3L, 1L);
            Count count2 = createCount(9L, 8L, 4L, 2L);

            when(postRepository.findByIdLessThanAndDeletedAtIsNullOrderByCreatedAtDesc(eq(cursor), any(Pageable.class)))
                    .thenReturn(posts);
            when(countRepository.findByPostId(8L)).thenReturn(Optional.of(count1));
            when(countRepository.findByPostId(9L)).thenReturn(Optional.of(count2));

            // when
            CursorPageResponseDto<PostResponseDto> result = postService.getPostList(cursor, size);

            // then
            assertThat(result.getPosts()).hasSize(2);
            assertThat(result.getHasNext()).isFalse();
            verify(postRepository).findByIdLessThanAndDeletedAtIsNullOrderByCreatedAtDesc(eq(cursor), any(Pageable.class));
        }

        @Test
        @DisplayName("다음 페이지 없음 (hasNext = false)")
        void getPostList_NoNextPage() {
            // given
            Long cursor = null;
            int size = 5;

            User user = new User();
            user.setId(1L);
            user.setNickname("author1");

            Post post1 = createPost(1L, "Title 1", "Content 1", user);
            Post post2 = createPost(2L, "Title 2", "Content 2", user);

            List<Post> posts = Arrays.asList(post1, post2);

            when(postRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(any(Pageable.class)))
                    .thenReturn(posts);
            when(countRepository.findByPostId(anyLong())).thenReturn(Optional.empty());

            // when
            CursorPageResponseDto<PostResponseDto> result = postService.getPostList(cursor, size);

            // then
            assertThat(result.getPosts()).hasSize(2);
            assertThat(result.getHasNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("게시글 상세 조회 테스트")
    class GetPostContentTest {

        @Test
        @DisplayName("본인 게시글 조회 성공 (isMine = true)")
        void getPostContent_OwnPost_Success() {
            // given
            Long postId = 1L;
            String email = "test@example.com";

            User user = new User();
            user.setId(1L);
            user.setEmail(email);

            Post post = createPost(postId, "My Post", "My Content", user);

            Count count = createCount(postId, 10L, 5L, 2L);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(postRepository.findByWithUser(postId)).thenReturn(Optional.of(post));
            when(imageRepository.findByPostIdAndDeletedAtIsNullOrderByDisplayOrderAsc(postId))
                    .thenReturn(List.of());
            when(countRepository.findByPostId(postId)).thenReturn(Optional.of(count));

            // when
            PostDetailResponseDto result = postService.getPostContent(postId, email);

            // then
            assertThat(result.getId()).isEqualTo(postId);
            assertThat(result.getTitle()).isEqualTo("My Post");
            assertThat(result.isMine()).isTrue();
            verify(userRepository).findByEmail(email);
            verify(postRepository).findByWithUser(postId);
        }

        @Test
        @DisplayName("타인 게시글 조회 성공 (isMine = false)")
        void getPostContent_OthersPost_Success() {
            // given
            Long postId = 1L;
            String email = "test@example.com";

            User viewer = new User();
            viewer.setId(1L);
            viewer.setEmail(email);

            User author = new User();
            author.setId(2L);
            author.setEmail("author@example.com");
            author.setNickname("author");

            Post post = createPost(postId, "Others Post", "Others Content", author);

            Count count = createCount(postId, 15L, 8L, 3L);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(viewer));
            when(postRepository.findByWithUser(postId)).thenReturn(Optional.of(post));
            when(imageRepository.findByPostIdAndDeletedAtIsNullOrderByDisplayOrderAsc(postId))
                    .thenReturn(List.of());
            when(countRepository.findByPostId(postId)).thenReturn(Optional.of(count));

            // when
            PostDetailResponseDto result = postService.getPostContent(postId, email);

            // then
            assertThat(result.getId()).isEqualTo(postId);
            assertThat(result.isMine()).isFalse();
            assertThat(result.getAuthor()).isEqualTo("author");
        }

        @Test
        @DisplayName("게시글을 찾을 수 없는 경우 예외 발생")
        void getPostContent_PostNotFound_ThrowsException() {
            // given
            Long postId = 999L;
            String email = "test@example.com";

            User user = new User();
            user.setId(1L);
            user.setEmail(email);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(postRepository.findByWithUser(postId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> postService.getPostContent(postId, email))
                    .isInstanceOf(PostNotFoundException.class)
                    .hasMessage("Not found post");

            verify(postRepository).findByWithUser(postId);
        }

        @Test
        @DisplayName("사용자를 찾을 수 없는 경우 예외 발생")
        void getPostContent_UserNotFound_ThrowsException() {
            // given
            Long postId = 1L;
            String email = "notfound@example.com";

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> postService.getPostContent(postId, email))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User not found");

            verify(userRepository).findByEmail(email);
            verify(postRepository, never()).findByWithUser(anyLong());
        }
    }

    @Nested
    @DisplayName("게시글 수정 테스트")
    class ModifyPostContentTest {

        @Test
        @DisplayName("제목만 수정 성공")
        void modifyPostContent_TitleOnly_Success() {
            // given
            Long postId = 1L;
            String email = "test@example.com";

            User user = new User();
            user.setId(1L);
            user.setEmail(email);

            Post post = createPost(postId, "Old Title", "Old Content", user);

            ModifyPostRequestDto requestDto = new ModifyPostRequestDto();
            requestDto.title = "New Title";
            requestDto.content = null;

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(postRepository.findById(postId)).thenReturn(Optional.of(post));

            // when
            CrudPostResponseDto result = postService.modifyPostContent(postId, email, requestDto);

            // then
            assertThat(result.getPostId()).isEqualTo(postId);
            assertThat(post.getTitle()).isEqualTo("New Title");
            assertThat(post.getContent()).isEqualTo("Old Content");
            verify(userRepository).findByEmail(email);
            verify(postRepository).findById(postId);
        }

        @Test
        @DisplayName("내용만 수정 성공")
        void modifyPostContent_ContentOnly_Success() {
            // given
            Long postId = 1L;
            String email = "test@example.com";

            User user = new User();
            user.setId(1L);
            user.setEmail(email);

            Post post = createPost(postId, "Old Title", "Old Content", user);

            ModifyPostRequestDto requestDto = new ModifyPostRequestDto();
            requestDto.title = null;
            requestDto.content = "New Content";

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(postRepository.findById(postId)).thenReturn(Optional.of(post));

            // when
            CrudPostResponseDto result = postService.modifyPostContent(postId, email, requestDto);

            // then
            assertThat(result.getPostId()).isEqualTo(postId);
            assertThat(post.getTitle()).isEqualTo("Old Title");
            assertThat(post.getContent()).isEqualTo("New Content");
        }

        @Test
        @DisplayName("제목과 내용 모두 수정 성공")
        void modifyPostContent_BothTitleAndContent_Success() {
            // given
            Long postId = 1L;
            String email = "test@example.com";

            User user = new User();
            user.setId(1L);
            user.setEmail(email);

            Post post = createPost(postId, "Old Title", "Old Content", user);

            ModifyPostRequestDto requestDto = new ModifyPostRequestDto();
            requestDto.title = "New Title";
            requestDto.content = "New Content";

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(postRepository.findById(postId)).thenReturn(Optional.of(post));

            // when
            CrudPostResponseDto result = postService.modifyPostContent(postId, email, requestDto);

            // then
            assertThat(result.getPostId()).isEqualTo(postId);
            assertThat(post.getTitle()).isEqualTo("New Title");
            assertThat(post.getContent()).isEqualTo("New Content");
        }

        @Test
        @DisplayName("작성자가 아닌 경우 권한 예외 발생")
        void modifyPostContent_NotAuthor_ThrowsUnauthorizedException() {
            // given
            Long postId = 1L;
            String email = "test@example.com";

            User currentUser = new User();
            currentUser.setId(1L);
            currentUser.setEmail(email);

            User author = new User();
            author.setId(2L);
            author.setEmail("author@example.com");

            Post post = createPost(postId, "Title", "Content", author);

            ModifyPostRequestDto requestDto = new ModifyPostRequestDto();
            requestDto.title = "New Title";

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(currentUser));
            when(postRepository.findById(postId)).thenReturn(Optional.of(post));

            // when & then
            assertThatThrownBy(() -> postService.modifyPostContent(postId, email, requestDto))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("You are not authorized to modify this post");

            verify(userRepository).findByEmail(email);
            verify(postRepository).findById(postId);
        }

        @Test
        @DisplayName("게시글을 찾을 수 없는 경우 예외 발생")
        void modifyPostContent_PostNotFound_ThrowsException() {
            // given
            Long postId = 999L;
            String email = "test@example.com";

            User user = new User();
            user.setId(1L);
            user.setEmail(email);

            ModifyPostRequestDto requestDto = new ModifyPostRequestDto();
            requestDto.title = "New Title";

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(postRepository.findById(postId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> postService.modifyPostContent(postId, email, requestDto))
                    .isInstanceOf(PostNotFoundException.class)
                    .hasMessage("Post not found");

            verify(postRepository).findById(postId);
        }

        @Test
        @DisplayName("사용자를 찾을 수 없는 경우 예외 발생")
        void modifyPostContent_UserNotFound_ThrowsException() {
            // given
            Long postId = 1L;
            String email = "notfound@example.com";

            ModifyPostRequestDto requestDto = new ModifyPostRequestDto();
            requestDto.title = "New Title";

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> postService.modifyPostContent(postId, email, requestDto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User not found");

            verify(userRepository).findByEmail(email);
            verify(postRepository, never()).findById(anyLong());
        }
    }

    @Nested
    @DisplayName("게시글 삭제 테스트")
    class RemovePostTest {

        @Test
        @DisplayName("게시글 삭제 성공 (댓글도 함께 삭제)")
        void removePost_Success_WithComments() {
            // given
            Long postId = 1L;
            String email = "test@example.com";

            User user = new User();
            user.setId(1L);
            user.setEmail(email);

            Post post = createPost(postId, "Title", "Content", user);

            Comment comment1 = new Comment();
            comment1.setId(1L);
            Comment comment2 = new Comment();
            comment2.setId(2L);
            List<Comment> comments = Arrays.asList(comment1, comment2);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(postRepository.findById(postId)).thenReturn(Optional.of(post));
            when(commentRepository.findByPostId(postId)).thenReturn(comments);

            // when
            CrudPostResponseDto result = postService.removePost(postId, email);

            // then
            assertThat(result.getPostId()).isEqualTo(postId);
            assertThat(post.getDeletedAt()).isNotNull();
            assertThat(comment1.getDeletedAt()).isNotNull();
            assertThat(comment2.getDeletedAt()).isNotNull();
            verify(userRepository).findByEmail(email);
            verify(postRepository).findById(postId);
            verify(commentRepository).findByPostId(postId);
        }

        @Test
        @DisplayName("작성자가 아닌 경우 권한 예외 발생")
        void removePost_NotAuthor_ThrowsUnauthorizedException() {
            // given
            Long postId = 1L;
            String email = "test@example.com";

            User currentUser = new User();
            currentUser.setId(1L);
            currentUser.setEmail(email);

            User author = new User();
            author.setId(2L);
            author.setEmail("author@example.com");

            Post post = createPost(postId, "Title", "Content", author);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(currentUser));
            when(postRepository.findById(postId)).thenReturn(Optional.of(post));

            // when & then
            assertThatThrownBy(() -> postService.removePost(postId, email))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("You are not authorized to delete this post");

            verify(userRepository).findByEmail(email);
            verify(postRepository).findById(postId);
            verify(commentRepository, never()).findByPostId(anyLong());
        }

        @Test
        @DisplayName("게시글을 찾을 수 없는 경우 예외 발생")
        void removePost_PostNotFound_ThrowsException() {
            // given
            Long postId = 999L;
            String email = "test@example.com";

            User user = new User();
            user.setId(1L);
            user.setEmail(email);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(postRepository.findById(postId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> postService.removePost(postId, email))
                    .isInstanceOf(PostNotFoundException.class)
                    .hasMessage("Post not found");

            verify(postRepository).findById(postId);
            verify(commentRepository, never()).findByPostId(anyLong());
        }

        @Test
        @DisplayName("사용자를 찾을 수 없는 경우 예외 발생")
        void removePost_UserNotFound_ThrowsException() {
            // given
            Long postId = 1L;
            String email = "notfound@example.com";

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> postService.removePost(postId, email))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User not found");

            verify(userRepository).findByEmail(email);
            verify(postRepository, never()).findById(anyLong());
        }
    }

    // Helper methods
    private Post createPost(Long id, String title, String content, User user) {
        Post post = new Post();
        post.setId(id);
        post.setTitle(title);
        post.setContent(content);
        post.setUser(user);
        post.setCreatedAt(LocalDateTime.now());
        return post;
    }

    private Count createCount(Long postId, Long viewCount, Long likeCount, Long commentCount) {
        Count count = new Count();
        count.setViewCount(viewCount);
        count.setLikeCount(likeCount);
        count.setCommentCount(commentCount);
        return count;
    }
}