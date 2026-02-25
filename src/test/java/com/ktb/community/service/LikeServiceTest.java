package com.ktb.community.service;

import com.ktb.community.dto.response.LikeResponseDto;
import com.ktb.community.entity.*;
import com.ktb.community.exception.custom.NotExistLikeException;
import com.ktb.community.exception.custom.PostNotFoundException;
import com.ktb.community.exception.custom.UserNotFoundException;
import com.ktb.community.jwt.JwtUtil;
import com.ktb.community.repository.CountRepository;
import com.ktb.community.repository.LikeRepository;
import com.ktb.community.repository.PostRepository;
import com.ktb.community.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("LikeService 테스트")
public class LikeServiceTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private LikeRepository likeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private CountRepository countRepository;

    @InjectMocks
    private LikeService likeService;

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
    @DisplayName("좋아요 추가 테스트")
    class LikePostTest {

        @Test
        @DisplayName("새로운 좋아요 생성 성공")
        void likePost_NewLike_Success() {
            // given
            Long postId = 1L;
            String email = "test@example.com";

            User user = createUser(1L, email, "user1");
            Post post = createPost(postId, "Test Post");
            LikePK likePK = new LikePK(user.getId(), postId);
            Count count = createCount(postId, 10L, 5L, 3L);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(postRepository.findById(postId)).thenReturn(Optional.of(post));
            when(likeRepository.findById(likePK)).thenReturn(Optional.empty());
            when(countRepository.findByPostId(postId)).thenReturn(Optional.of(count));
            when(likeRepository.save(any(Like.class))).thenReturn(new Like());

            // when
            LikeResponseDto result = likeService.likePost(postId, email);

            // then
            assertThat(result.getPostId()).isEqualTo(postId);
            assertThat(result.isLiked()).isTrue();
            assertThat(count.getLikeCount()).isEqualTo(6L);
            verify(userRepository).findByEmail(email);
            verify(postRepository).findById(postId);
            verify(likeRepository).findById(likePK);
            verify(likeRepository).save(any(Like.class));
            verify(countRepository).findByPostId(postId);
        }

        @Test
        @DisplayName("삭제된 좋아요 복구 성공")
        void likePost_RestoreDeletedLike_Success() {
            // given
            Long postId = 1L;
            String email = "test@example.com";

            User user = createUser(1L, email, "user1");
            Post post = createPost(postId, "Test Post");
            LikePK likePK = new LikePK(user.getId(), postId);
            Count count = createCount(postId, 10L, 5L, 3L);

            Like deletedLike = new Like();
            deletedLike.setId(likePK);
            deletedLike.setUser(user);
            deletedLike.setPost(post);
            deletedLike.setDeletedAt(LocalDateTime.now().minusDays(1));

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(postRepository.findById(postId)).thenReturn(Optional.of(post));
            when(likeRepository.findById(likePK)).thenReturn(Optional.of(deletedLike));
            when(countRepository.findByPostId(postId)).thenReturn(Optional.of(count));

            // when
            LikeResponseDto result = likeService.likePost(postId, email);

            // then
            assertThat(result.getPostId()).isEqualTo(postId);
            assertThat(result.isLiked()).isTrue();
            assertThat(deletedLike.getDeletedAt()).isNull();
            assertThat(count.getLikeCount()).isEqualTo(6L);
            verify(userRepository).findByEmail(email);
            verify(postRepository).findById(postId);
            verify(likeRepository).findById(likePK);
            verify(likeRepository, never()).save(any(Like.class));
        }

        @Test
        @DisplayName("이미 활성화된 좋아요 존재 (아무것도 안 함)")
        void likePost_AlreadyLiked_NoAction() {
            // given
            Long postId = 1L;
            String email = "test@example.com";

            User user = createUser(1L, email, "user1");
            Post post = createPost(postId, "Test Post");
            LikePK likePK = new LikePK(user.getId(), postId);
            Count count = createCount(postId, 10L, 5L, 3L);

            Like activeLike = new Like();
            activeLike.setId(likePK);
            activeLike.setUser(user);
            activeLike.setPost(post);
            activeLike.setDeletedAt(null);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(postRepository.findById(postId)).thenReturn(Optional.of(post));
            when(likeRepository.findById(likePK)).thenReturn(Optional.of(activeLike));
            when(countRepository.findByPostId(postId)).thenReturn(Optional.of(count));

            // when
            LikeResponseDto result = likeService.likePost(postId, email);

            // then
            assertThat(result.getPostId()).isEqualTo(postId);
            assertThat(result.isLiked()).isTrue();
            assertThat(count.getLikeCount()).isEqualTo(5L); // 변경 없음
            verify(userRepository).findByEmail(email);
            verify(postRepository).findById(postId);
            verify(likeRepository).findById(likePK);
            verify(likeRepository, never()).save(any(Like.class));
        }

        @Test
        @DisplayName("사용자를 찾을 수 없는 경우 예외 발생")
        void likePost_UserNotFound_ThrowsException() {
            // given
            Long postId = 1L;
            String email = "notfound@example.com";

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> likeService.likePost(postId, email))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("Not found User");

            verify(userRepository).findByEmail(email);
            verify(postRepository, never()).findById(anyLong());
            verify(likeRepository, never()).findById(any(LikePK.class));
        }

        @Test
        @DisplayName("게시글을 찾을 수 없는 경우 예외 발생")
        void likePost_PostNotFound_ThrowsException() {
            // given
            Long postId = 999L;
            String email = "test@example.com";

            User user = createUser(1L, email, "user1");

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(postRepository.findById(postId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> likeService.likePost(postId, email))
                    .isInstanceOf(PostNotFoundException.class)
                    .hasMessage("Not found Post");

            verify(userRepository).findByEmail(email);
            verify(postRepository).findById(postId);
            verify(likeRepository, never()).findById(any(LikePK.class));
        }

        @Test
        @DisplayName("Count를 찾을 수 없는 경우 예외 발생")
        void likePost_CountNotFound_ThrowsException() {
            // given
            Long postId = 1L;
            String email = "test@example.com";

            User user = createUser(1L, email, "user1");
            Post post = createPost(postId, "Test Post");
            LikePK likePK = new LikePK(user.getId(), postId);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(postRepository.findById(postId)).thenReturn(Optional.of(post));
            when(likeRepository.findById(likePK)).thenReturn(Optional.empty());
            when(countRepository.findByPostId(postId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> likeService.likePost(postId, email))
                    .isInstanceOf(PostNotFoundException.class)
                    .hasMessage("Not found post");

            verify(userRepository).findByEmail(email);
            verify(postRepository).findById(postId);
            verify(likeRepository).findById(likePK);
            verify(countRepository).findByPostId(postId);
            verify(likeRepository, never()).save(any(Like.class));
        }
    }

    @Nested
    @DisplayName("좋아요 취소 테스트")
    class UnLikePostTest {

        @Test
        @DisplayName("좋아요 취소 성공 (soft delete)")
        void unLikePost_Success() {
            // given
            Long postId = 1L;
            String email = "test@example.com";

            User user = createUser(1L, email, "user1");
            LikePK likePK = new LikePK(user.getId(), postId);
            Count count = createCount(postId, 10L, 5L, 3L);

            Like like = new Like();
            like.setId(likePK);
            like.setUser(user);
            like.setDeletedAt(null);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(likeRepository.findById(likePK)).thenReturn(Optional.of(like));
            when(countRepository.findByPostId(postId)).thenReturn(Optional.of(count));

            // when
            LikeResponseDto result = likeService.unLikePost(postId, email);

            // then
            assertThat(result.getPostId()).isEqualTo(postId);
            assertThat(result.isLiked()).isFalse();
            assertThat(like.getDeletedAt()).isNotNull();
            assertThat(count.getLikeCount()).isEqualTo(4L);
            verify(userRepository).findByEmail(email);
            verify(likeRepository).findById(likePK);
            verify(countRepository).findByPostId(postId);
        }

        @Test
        @DisplayName("사용자를 찾을 수 없는 경우 예외 발생")
        void unLikePost_UserNotFound_ThrowsException() {
            // given
            Long postId = 1L;
            String email = "notfound@example.com";

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> likeService.unLikePost(postId, email))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("User not found");

            verify(userRepository).findByEmail(email);
            verify(likeRepository, never()).findById(any(LikePK.class));
        }

        @Test
        @DisplayName("좋아요를 찾을 수 없는 경우 예외 발생")
        void unLikePost_LikeNotFound_ThrowsException() {
            // given
            Long postId = 1L;
            String email = "test@example.com";

            User user = createUser(1L, email, "user1");
            LikePK likePK = new LikePK(user.getId(), postId);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(likeRepository.findById(likePK)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> likeService.unLikePost(postId, email))
                    .isInstanceOf(NotExistLikeException.class)
                    .hasMessage("Not exist like");

            verify(userRepository).findByEmail(email);
            verify(likeRepository).findById(likePK);
            verify(countRepository, never()).findByPostId(anyLong());
        }

        @Test
        @DisplayName("Count를 찾을 수 없는 경우 예외 발생")
        void unLikePost_CountNotFound_ThrowsException() {
            // given
            Long postId = 1L;
            String email = "test@example.com";

            User user = createUser(1L, email, "user1");
            LikePK likePK = new LikePK(user.getId(), postId);

            Like like = new Like();
            like.setId(likePK);
            like.setUser(user);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(likeRepository.findById(likePK)).thenReturn(Optional.of(like));
            when(countRepository.findByPostId(postId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> likeService.unLikePost(postId, email))
                    .isInstanceOf(PostNotFoundException.class)
                    .hasMessage("Not found post");

            verify(userRepository).findByEmail(email);
            verify(likeRepository).findById(likePK);
            verify(countRepository).findByPostId(postId);
        }
    }

    // Helper methods
    private User createUser(Long id, String email, String nickname) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setNickname(nickname);
        return user;
    }

    private Post createPost(Long id, String title) {
        Post post = new Post();
        post.setId(id);
        post.setTitle(title);
        return post;
    }

    private Count createCount(Long postId, Long viewCount, Long likeCount, Long commentCount) {
        Count count = new Count();
        count.setLikeCount(likeCount);
        count.setViewCount(viewCount);
        count.setCommentCount(commentCount);
        return count;
    }
}