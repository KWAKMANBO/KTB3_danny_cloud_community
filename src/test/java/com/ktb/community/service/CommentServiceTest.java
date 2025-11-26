package com.ktb.community.service;

import com.ktb.community.dto.request.CreateCommentRequestDto;
import com.ktb.community.dto.request.UpdateCommentRequestDto;
import com.ktb.community.dto.response.CommentResponseDto;
import com.ktb.community.dto.response.CrudCommentResponseDto;
import com.ktb.community.dto.response.CursorCommentResponseDto;
import com.ktb.community.entity.Comment;
import com.ktb.community.entity.Post;
import com.ktb.community.entity.User;
import com.ktb.community.exception.custom.CommentNotFoundException;
import com.ktb.community.exception.custom.PostNotFoundException;
import com.ktb.community.exception.custom.UnauthorizedException;
import com.ktb.community.exception.custom.UserNotFoundException;
import com.ktb.community.jwt.JwtUtil;
import com.ktb.community.repository.CommentRepository;
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
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CommentService 테스트")
public class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private CommentService commentService;

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
    @DisplayName("댓글 목록 조회 테스트")
    class GetCommentListTest {

        @Test
        @DisplayName("첫 페이지 조회 성공 (cursor = null)")
        void getCommentList_FirstPage_Success() {
            // given
            Long postId = 1L;
            Long cursor = null;
            int size = 2;
            String email = "test@example.com";

            User user = createUser(1L, email, "user1");

            Comment comment1 = createComment(1L, "Comment 1", user);
            Comment comment2 = createComment(2L, "Comment 2", user);
            Comment comment3 = createComment(3L, "Comment 3", user);

            List<Comment> comments = Arrays.asList(comment1, comment2, comment3);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(commentRepository.findByPostIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq(postId), any(Pageable.class)))
                    .thenReturn(comments);

            // when
            CursorCommentResponseDto<CommentResponseDto> result = commentService.getCommentList(postId, cursor, size, email);

            // then
            assertThat(result.getComments()).hasSize(2);
            assertThat(result.getHasNext()).isTrue();
            assertThat(result.getNextCursor()).isEqualTo(2L);
            verify(userRepository).findByEmail(email);
            verify(commentRepository).findByPostIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq(postId), any(Pageable.class));
        }

        @Test
        @DisplayName("다음 페이지 조회 성공 (cursor 있음)")
        void getCommentList_NextPage_Success() {
            // given
            Long postId = 1L;
            Long cursor = 10L;
            int size = 2;
            String email = "test@example.com";

            User user = createUser(1L, email, "user1");

            Comment comment1 = createComment(8L, "Comment 8", user);
            Comment comment2 = createComment(9L, "Comment 9", user);

            List<Comment> comments = Arrays.asList(comment1, comment2);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(commentRepository.findByPostIdAndIdLessThanAndDeletedAtIsNullOrderByCreatedAtDesc(eq(postId), eq(cursor), any(Pageable.class)))
                    .thenReturn(comments);

            // when
            CursorCommentResponseDto<CommentResponseDto> result = commentService.getCommentList(postId, cursor, size, email);

            // then
            assertThat(result.getComments()).hasSize(2);
            assertThat(result.getHasNext()).isFalse();
            assertThat(result.getNextCursor()).isEqualTo(9L);
            verify(commentRepository).findByPostIdAndIdLessThanAndDeletedAtIsNullOrderByCreatedAtDesc(eq(postId), eq(cursor), any(Pageable.class));
        }

        @Test
        @DisplayName("다음 페이지 없음 (hasNext = false)")
        void getCommentList_NoNextPage() {
            // given
            Long postId = 1L;
            Long cursor = null;
            int size = 5;
            String email = "test@example.com";

            User user = createUser(1L, email, "user1");

            Comment comment1 = createComment(1L, "Comment 1", user);
            Comment comment2 = createComment(2L, "Comment 2", user);

            List<Comment> comments = Arrays.asList(comment1, comment2);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(commentRepository.findByPostIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq(postId), any(Pageable.class)))
                    .thenReturn(comments);

            // when
            CursorCommentResponseDto<CommentResponseDto> result = commentService.getCommentList(postId, cursor, size, email);

            // then
            assertThat(result.getComments()).hasSize(2);
            assertThat(result.getHasNext()).isFalse();
        }

        @Test
        @DisplayName("본인 댓글 확인 (isMine = true)")
        void getCommentList_OwnComment_IsMineTrue() {
            // given
            Long postId = 1L;
            Long cursor = null;
            int size = 2;
            String email = "test@example.com";

            User user = createUser(1L, email, "user1");

            Comment comment1 = createComment(1L, "My Comment", user);

            List<Comment> comments = List.of(comment1);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(commentRepository.findByPostIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq(postId), any(Pageable.class)))
                    .thenReturn(comments);

            // when
            CursorCommentResponseDto<CommentResponseDto> result = commentService.getCommentList(postId, cursor, size, email);

            // then
            assertThat(result.getComments()).hasSize(1);
            assertThat(result.getComments().get(0).isMine()).isTrue();
        }

        @Test
        @DisplayName("타인 댓글 확인 (isMine = false)")
        void getCommentList_OthersComment_IsMineFalse() {
            // given
            Long postId = 1L;
            Long cursor = null;
            int size = 2;
            String email = "test@example.com";

            User viewer = createUser(1L, email, "viewer");
            User author = createUser(2L, "author@example.com", "author");

            Comment comment1 = createComment(1L, "Others Comment", author);

            List<Comment> comments = List.of(comment1);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(viewer));
            when(commentRepository.findByPostIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq(postId), any(Pageable.class)))
                    .thenReturn(comments);

            // when
            CursorCommentResponseDto<CommentResponseDto> result = commentService.getCommentList(postId, cursor, size, email);

            // then
            assertThat(result.getComments()).hasSize(1);
            assertThat(result.getComments().get(0).isMine()).isFalse();
            assertThat(result.getComments().get(0).getAuthor()).isEqualTo("author");
        }

        @Test
        @DisplayName("사용자를 찾을 수 없는 경우 예외 발생")
        void getCommentList_UserNotFound_ThrowsException() {
            // given
            Long postId = 1L;
            Long cursor = null;
            int size = 2;
            String email = "notfound@example.com";

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> commentService.getCommentList(postId, cursor, size, email))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("User not found");

            verify(userRepository).findByEmail(email);
            verify(commentRepository, never()).findByPostIdAndDeletedAtIsNullOrderByCreatedAtDesc(anyLong(), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("댓글 작성 테스트")
    class WriteCommentTest {

        @Test
        @DisplayName("댓글 작성 성공")
        void writeComment_Success() {
            // given
            Long postId = 1L;
            String email = "test@example.com";
            CreateCommentRequestDto requestDto = CreateCommentRequestDto.builder()
                    .content("Test Comment")
                    .build();

            User user = createUser(1L, email, "user1");
            Post post = new Post();
            post.setId(postId);

            Comment savedComment = new Comment();
            savedComment.setId(1L);
            savedComment.setContent("Test Comment");
            savedComment.setUser(user);
            savedComment.setPost(post);

            when(postRepository.findById(postId)).thenReturn(Optional.of(post));
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);

            // when
            CrudCommentResponseDto result = commentService.writeComment(postId, email, requestDto);

            // then
            assertThat(result.getCommentId()).isEqualTo(1L);
            verify(postRepository).findById(postId);
            verify(userRepository).findByEmail(email);
            verify(commentRepository).save(any(Comment.class));
        }

        @Test
        @DisplayName("게시글을 찾을 수 없는 경우 예외 발생")
        void writeComment_PostNotFound_ThrowsException() {
            // given
            Long postId = 999L;
            String email = "test@example.com";
            CreateCommentRequestDto requestDto = CreateCommentRequestDto.builder()
                    .content("Test Comment")
                    .build();

            when(postRepository.findById(postId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> commentService.writeComment(postId, email, requestDto))
                    .isInstanceOf(PostNotFoundException.class)
                    .hasMessage("Not found post");

            verify(postRepository).findById(postId);
            verify(userRepository, never()).findByEmail(anyString());
            verify(commentRepository, never()).save(any(Comment.class));
        }

        @Test
        @DisplayName("사용자를 찾을 수 없는 경우 예외 발생")
        void writeComment_UserNotFound_ThrowsException() {
            // given
            Long postId = 1L;
            String email = "notfound@example.com";
            CreateCommentRequestDto requestDto = CreateCommentRequestDto.builder()
                    .content("Test Comment")
                    .build();

            Post post = new Post();
            post.setId(postId);

            when(postRepository.findById(postId)).thenReturn(Optional.of(post));
            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> commentService.writeComment(postId, email, requestDto))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("Not found user");

            verify(postRepository).findById(postId);
            verify(userRepository).findByEmail(email);
            verify(commentRepository, never()).save(any(Comment.class));
        }
    }

    @Nested
    @DisplayName("댓글 수정 테스트")
    class ModifyCommentTest {

        @Test
        @DisplayName("댓글 수정 성공")
        void modifyComment_Success() {
            // given
            String email = "test@example.com";
            UpdateCommentRequestDto requestDto = new UpdateCommentRequestDto(1L, "Updated Comment");

            User user = createUser(1L, email, "user1");
            Comment comment = createComment(1L, "Old Comment", user);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

            // when
            CrudCommentResponseDto result = commentService.modifyComment(email, requestDto);

            // then
            assertThat(result.getCommentId()).isEqualTo(1L);
            assertThat(comment.getContent()).isEqualTo("Updated Comment");
            verify(userRepository).findByEmail(email);
            verify(commentRepository).findById(1L);
        }

        @Test
        @DisplayName("작성자가 아닌 경우 권한 예외 발생")
        void modifyComment_NotAuthor_ThrowsUnauthorizedException() {
            // given
            String email = "test@example.com";
            UpdateCommentRequestDto requestDto = new UpdateCommentRequestDto(1L, "Updated Comment");

            User currentUser = createUser(1L, email, "user1");
            User author = createUser(2L, "author@example.com", "author");
            Comment comment = createComment(1L, "Comment", author);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(currentUser));
            when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

            // when & then
            assertThatThrownBy(() -> commentService.modifyComment(email, requestDto))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("You are not authorized to modify this comment");

            verify(userRepository).findByEmail(email);
            verify(commentRepository).findById(1L);
        }

        @Test
        @DisplayName("댓글을 찾을 수 없는 경우 예외 발생")
        void modifyComment_CommentNotFound_ThrowsException() {
            // given
            String email = "test@example.com";
            UpdateCommentRequestDto requestDto = new UpdateCommentRequestDto(999L, "Updated Comment");

            User user = createUser(1L, email, "user1");

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(commentRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> commentService.modifyComment(email, requestDto))
                    .isInstanceOf(CommentNotFoundException.class)
                    .hasMessage("Not found comment");

            verify(userRepository).findByEmail(email);
            verify(commentRepository).findById(999L);
        }

        @Test
        @DisplayName("사용자를 찾을 수 없는 경우 예외 발생")
        void modifyComment_UserNotFound_ThrowsException() {
            // given
            String email = "notfound@example.com";
            UpdateCommentRequestDto requestDto = new UpdateCommentRequestDto(1L, "Updated Comment");

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> commentService.modifyComment(email, requestDto))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("User not found");

            verify(userRepository).findByEmail(email);
            verify(commentRepository, never()).findById(anyLong());
        }
    }

    @Nested
    @DisplayName("댓글 삭제 테스트")
    class RemoveCommentTest {

        @Test
        @DisplayName("댓글 삭제 성공 (soft delete)")
        void removeComment_Success() {
            // given
            Long commentId = 1L;
            String email = "test@example.com";

            User user = createUser(1L, email, "user1");
            Comment comment = createComment(commentId, "Comment to delete", user);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

            // when
            CrudCommentResponseDto result = commentService.removeComment(commentId, email);

            // then
            assertThat(result.getCommentId()).isEqualTo(commentId);
            assertThat(comment.getDeletedAt()).isNotNull();
            verify(userRepository).findByEmail(email);
            verify(commentRepository).findById(commentId);
        }

        @Test
        @DisplayName("작성자가 아닌 경우 권한 예외 발생")
        void removeComment_NotAuthor_ThrowsUnauthorizedException() {
            // given
            Long commentId = 1L;
            String email = "test@example.com";

            User currentUser = createUser(1L, email, "user1");
            User author = createUser(2L, "author@example.com", "author");
            Comment comment = createComment(commentId, "Comment", author);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(currentUser));
            when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

            // when & then
            assertThatThrownBy(() -> commentService.removeComment(commentId, email))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("You are not authorized to delete this comment");

            verify(userRepository).findByEmail(email);
            verify(commentRepository).findById(commentId);
        }

        @Test
        @DisplayName("댓글을 찾을 수 없는 경우 예외 발생")
        void removeComment_CommentNotFound_ThrowsException() {
            // given
            Long commentId = 999L;
            String email = "test@example.com";

            User user = createUser(1L, email, "user1");

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> commentService.removeComment(commentId, email))
                    .isInstanceOf(CommentNotFoundException.class)
                    .hasMessage("Not found comment");

            verify(userRepository).findByEmail(email);
            verify(commentRepository).findById(commentId);
        }

        @Test
        @DisplayName("사용자를 찾을 수 없는 경우 예외 발생")
        void removeComment_UserNotFound_ThrowsException() {
            // given
            Long commentId = 1L;
            String email = "notfound@example.com";

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> commentService.removeComment(commentId, email))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("User not found");

            verify(userRepository).findByEmail(email);
            verify(commentRepository, never()).findById(anyLong());
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

    private Comment createComment(Long id, String content, User user) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setContent(content);
        comment.setUser(user);
        comment.setCreatedAt(LocalDateTime.now());
        return comment;
    }
}