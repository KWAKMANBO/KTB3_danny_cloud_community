package com.ktb.community.controller;

import com.ktb.community.dto.request.CreateCommentRequestDto;
import com.ktb.community.dto.request.CreatePostRequestDto;
import com.ktb.community.dto.request.ModifyPostRequestDto;
import com.ktb.community.dto.request.UpdateCommentRequestDto;
import com.ktb.community.dto.response.*;
import com.ktb.community.entity.Comment;
import com.ktb.community.service.SessionCommentService;
import com.ktb.community.service.SessionLikeService;
import com.ktb.community.service.SessionPostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/posts")
@Profile("session")
public class SessionPostController {
    private final SessionPostService sessionPostService;
    private final SessionCommentService sessionCommentService;
    private final SessionLikeService sessionLikeService;

    public SessionPostController(SessionPostService sessionPostService, SessionCommentService sessionCommentService, SessionLikeService sessionLikeService) {
        this.sessionPostService = sessionPostService;
        this.sessionCommentService = sessionCommentService;
        this.sessionLikeService = sessionLikeService;
    }

    @GetMapping()
    public ResponseEntity<ApiResponseDto<CursorPageResponseDto<PostResponseDto>>> getPosts(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        CursorPageResponseDto<PostResponseDto> result = sessionPostService.getPostList(cursor, size);
        return ResponseEntity.ok(ApiResponseDto.success(result));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponseDto<PostDetailResponseDto>> getPostDetail(
            @PathVariable @Positive Long postId,
            @CookieValue("SID") String sid) {
        PostDetailResponseDto post = this.sessionPostService.getPostContent(postId, sid);
        return ResponseEntity.ok().body(ApiResponseDto.success(post));
    }

    @GetMapping("/{postId}/comments")
    public ResponseEntity<ApiResponseDto<?>> getComment(@PathVariable Long postId, @RequestParam(required = false) Long cursor,
                                                        @RequestParam(defaultValue = "5") int size, @CookieValue("SID") String sid) {

        CursorCommentResponseDto<CommentResponseDto> cursorCommentResponseDto = this.sessionCommentService.getCommentList(postId, cursor, size, sid);
        return ResponseEntity.ok().body(ApiResponseDto.success(cursorCommentResponseDto));
    }


    @PostMapping()
    public ResponseEntity<ApiResponseDto<CrudPostResponseDto>> createPost(@RequestBody @Valid CreatePostRequestDto createPostRequestDto,
                                                                          @CookieValue("SID") String sid) {

        CrudPostResponseDto crudPostResponseDto = sessionPostService.createPost(createPostRequestDto, sid);
        return ResponseEntity.ok().body(ApiResponseDto.success(crudPostResponseDto));
    }


    @PatchMapping("/{postId}")
    public ResponseEntity<ApiResponseDto<?>> modifyPost(
            @PathVariable Long postId,
            @RequestBody ModifyPostRequestDto modifyPostRequestDto,
            @CookieValue("SID") String sid) {
        CrudPostResponseDto modifiedPost = this.sessionPostService.modifyPostContent(postId, modifyPostRequestDto, sid);
        return ResponseEntity.ok().body(ApiResponseDto.success(modifiedPost));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponseDto<?>> deletePost(@PathVariable Long postId, @CookieValue("SID") String sid) {
        CrudPostResponseDto crudPostResponseDto = this.sessionPostService.removePost(postId, sid);
        return ResponseEntity.ok().body(ApiResponseDto.success(crudPostResponseDto));
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<ApiResponseDto<?>> createComment(@PathVariable Long postId,
                                                           @RequestBody @Valid CreateCommentRequestDto createCommentRequestDto,
                                                           @CookieValue("SID") String sid) {

        CrudCommentResponseDto crudCommentResponseDto = this.sessionCommentService.writeComment(postId, sid, createCommentRequestDto);
        return ResponseEntity.ok().body(ApiResponseDto.success(crudCommentResponseDto));
    }

    @PatchMapping("/{postId}/comments")
    public ResponseEntity<ApiResponseDto<?>> updateComment(@PathVariable Long postId, @RequestBody @Valid UpdateCommentRequestDto updateCommentRequestDto,
                                                           @CookieValue("SID") String sid) {

        CrudCommentResponseDto updateCommentResponseDto = this.sessionCommentService.modifyComment(sid, updateCommentRequestDto);
        return ResponseEntity.ok().body(ApiResponseDto.success(updateCommentResponseDto));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponseDto<?>> deleteComment(@PathVariable Long commentId, @CookieValue("SID") String sid) {
        CrudCommentResponseDto deletedCommentResponseDto = this.sessionCommentService.removeComment(commentId, sid);

        return ResponseEntity.ok().body(ApiResponseDto.success(deletedCommentResponseDto));
    }

    @PostMapping("/{postId}/likes")
    public ResponseEntity<ApiResponseDto<?>> createLike(@PathVariable Long postId, @CookieValue("SID") String sid) {

        LikeResponseDto likeResponseDto = this.sessionLikeService.likePost(postId, sid);
        return ResponseEntity.ok().body(ApiResponseDto.success(likeResponseDto));
    }

    @DeleteMapping("/{postId}/likes")
    public ResponseEntity<ApiResponseDto<?>> deleteLike(@PathVariable Long postId, @CookieValue("SID") String sid) {

        LikeResponseDto likeResponseDto = this.sessionLikeService.unLikePost(postId,sid);
        return ResponseEntity.ok().body(ApiResponseDto.success(likeResponseDto));
    }


}
