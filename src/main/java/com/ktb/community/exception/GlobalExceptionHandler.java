package com.ktb.community.exception;

import com.ktb.community.dto.response.ApiResponseDto;
import com.ktb.community.exception.custom.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> handleDatabaseException(DataAccessException e) {
        System.err.println("[DataAccessException] " + e.getMessage());
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponseDto.error("A temporary database error has occurred. Please try again later."));
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ApiResponseDto<?>> handleNullPointer(NullPointerException e) {
        System.err.println("[NullPointerException] " + e.getMessage());
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponseDto.error("A null pointer error occurred."))
                ;
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponseDto<?>> handleDuplicateEmail(DuplicateEmailException e) {
        System.err.println("[DuplicateEmailException] " + e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ApiResponseDto<?>> handleInvalidPasswordException(InvalidPasswordException e) {
        System.err.println("[InvalidPasswordException] " + e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiResponseDto<?>> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        System.err.println("[InvalidRefreshTokenException] " + e.getMessage());
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponseDto<?>> handleInvalidCredentials(InvalidCredentialsException e) {
        System.err.println("[InvalidCredentialsException] " + e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponseDto<?>> handleUserNotFound(UserNotFoundException e) {
        System.err.println("[UserNotFoundException] " + e.getMessage());
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<ApiResponseDto<?>> handlePostNotFound(PostNotFoundException e) {
        System.err.println("[PostNotFoundException] " + e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDto<?>> handleIllegalArgument(IllegalArgumentException e) {
        System.err.println("[IllegalArgumentException] " + e.getMessage());
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponseDto<?>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Validation failed");

        System.err.println("[MethodArgumentNotValidException] " + errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDto.error(errorMessage));

    }

    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<ApiResponseDto<?>> handleCommentNotFoundException(CommentNotFoundException e) {
        System.err.println("[CommentNotFoundException] " + e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponseDto<?>> handleUnauthorizedException(UnauthorizedException e) {
        System.err.println("[UnauthorizedException] " + e.getMessage());
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(AlreadyExistLikeException.class)
    public ResponseEntity<ApiResponseDto<?>> handleAlreadyExistLikeException(AlreadyExistLikeException e) {
        System.err.println("[AlreadyExistLikeException] " + e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(InvalidNicknameException.class)
    public ResponseEntity<ApiResponseDto<?>> handleInvalidNicknameException(InvalidNicknameException e) {
        System.err.println("[InvalidNicknameException] " + e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(DuplicateNicknameException.class)
    public ResponseEntity<ApiResponseDto<?>> handleDuplicateNicknameException(DuplicateNicknameException e) {
        System.err.println("[DuplicateNicknameException] " + e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(InvalidImageKeyException.class)
    public ResponseEntity<ApiResponseDto<?>> handleInvalidImageKeyException(InvalidImageKeyException e) {
        System.err.println("[InvalidImageKeyException] " + e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(ImageNotFoundException.class)
    public ResponseEntity<ApiResponseDto<?>> handleImageNotFoundException(ImageNotFoundException e) {
        System.err.println("[ImageNotFoundException] " + e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(InvalidFileTypeException.class)
    public ResponseEntity<ApiResponseDto<?>> handleInvalidFileTypeException(InvalidFileTypeException e) {
        System.err.println("[InvalidFileTypeException] " + e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponseDto<?>> handleRateLimitExceededException(RateLimitExceededException e) {
        System.err.println("[RateLimitExceededException] " + e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiResponseDto.error(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<?>> handleGeneralException(Exception e) {
        System.err.println("=== Unexpected Exception Occurred ===");
        System.err.println("Exception type: " + e.getClass().getName());
        System.err.println("Exception message: " + e.getMessage());
        e.printStackTrace();
        System.err.println("=====================================");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponseDto.error("A temporary error has occurred. Please try again later."));
    }

}
