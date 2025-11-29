package com.ktb.community.service;

import com.ktb.community.dto.request.ChangePasswordRequestDto;
import com.ktb.community.dto.request.ModifyNicknameRequestDto;
import com.ktb.community.dto.response.AvailabilityResponseDto;
import com.ktb.community.dto.response.CrudUserResponseDto;
import com.ktb.community.dto.response.UserInfoResponseDto;
import com.ktb.community.entity.Comment;
import com.ktb.community.entity.Count;
import com.ktb.community.entity.Post;
import com.ktb.community.entity.User;
import com.ktb.community.exception.custom.DuplicateNicknameException;
import com.ktb.community.exception.custom.InvalidNicknameException;
import com.ktb.community.exception.custom.InvalidPasswordException;
import com.ktb.community.exception.custom.UserNotFoundException;
import com.ktb.community.jwt.JwtUtil;
import com.ktb.community.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final CountRepository countRepository;
    private final ImageRepository imageRepository;
    private final LikeRepository likeRepository;
    private final RefreshRepository refreshRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final ImageService imageService;

    @Autowired
    public UserService(UserRepository userRepository, PostRepository postRepository, CommentRepository commentRepository, CountRepository countRepository, ImageRepository imageRepository, LikeRepository likeRepository, RefreshRepository refreshRepository, JwtUtil jwtUtil, PasswordEncoder passwordEncoder, ImageService imageService) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.countRepository = countRepository;
        this.imageRepository = imageRepository;
        this.likeRepository = likeRepository;
        this.refreshRepository = refreshRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.imageService = imageService;
    }

    public AvailabilityResponseDto checkDuplicateEmail(String email) {
        return new AvailabilityResponseDto(!this.userRepository.existsByEmail(email));
    }

    public AvailabilityResponseDto checkValidityPassword(String password) {
        // 최소 8자, 소문자 1개 이상, 숫자 1개 이상, 특수문자 1개 이상
        String regex = "^(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,}$";
        boolean isValid = password.matches(regex);
        return new AvailabilityResponseDto(isValid);
    }

    public UserInfoResponseDto readMyInfo(String email) {
        User user = this.userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("Not found user."));

        // Private 버킷: Presigned Download URL 생성
        String profileImageUrl = null;
        if (user.getProfileImage() != null) {
            List<String> urls = imageService.generateDownloadUrls(List.of(user.getProfileImage()));
            profileImageUrl = urls.isEmpty() ? null : urls.get(0);
        }

        return new UserInfoResponseDto(user.getEmail(), user.getNickname(), profileImageUrl);
    }

    @Transactional
    public CrudUserResponseDto changeNickname(String email, ModifyNicknameRequestDto modifyNicknameRequestDto) {
        User user = this.userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Not found user"));

        String newNickname = modifyNicknameRequestDto.getNickname();

        if (user.getNickname().equals(newNickname)) {
            throw new InvalidNicknameException("Same nickname is not acceptable");
        }

        if (this.userRepository.existsByNicknameAndIdNot(newNickname, user.getId())) {
            throw new DuplicateNicknameException("This nickname is already in use");
        }

        user.setNickname(newNickname);
        return new CrudUserResponseDto(user.getId());
    }

    @Transactional
    public CrudUserResponseDto changePassword(String email, ChangePasswordRequestDto changePasswordRequestDto) {
        User user = this.userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Not found user"));

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(changePasswordRequestDto.getCurrentPassword(), user.getPassword())) {
            throw new InvalidPasswordException("Current password is incorrect");
        }

        // 새 비밀번호가 현재 비밀번호와 동일한지 확인
        if (changePasswordRequestDto.getCurrentPassword().equals(changePasswordRequestDto.getNewPassword())) {
            throw new InvalidPasswordException("New password must be different from current password");
        }

        // 새 비밀번호 유효성 검증
        if (!this.checkValidityPassword(changePasswordRequestDto.getNewPassword()).getIsAvailable()) {
            throw new InvalidPasswordException("New password does not meet requirements");
        }

        // 비밀번호 변경
        user.setPassword(passwordEncoder.encode(changePasswordRequestDto.getNewPassword()));
        return new CrudUserResponseDto(user.getId());
    }

    @Transactional
    public CrudUserResponseDto updateProfileImage(String email, String imageKey) {
        User user = this.userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // 기존 프로필 이미지 삭제 (S3)
//        if (user.getProfileImage() != null) {
//            imageService.deleteProfileImage(user.getId());
//        }

        // 새 프로필 이미지 확인 및 저장
//        String imageUrl = imageService.confirmProfileImageUpload(imageKey, user);
        String imageUrl = imageService.confirmProfileImageUpload(imageKey);
        user.setProfileImage(imageUrl);

        return new CrudUserResponseDto(user.getId());
    }

    @Transactional
    public CrudUserResponseDto deleteProfileImage(String email) {
        User user = this.userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // S3에서 프로필 이미지 삭제
//        if (user.getProfileImage() != null) {
//            imageService.deleteProfileImage(user.getId());
//            user.setProfileImage(null);
//        }

        return new CrudUserResponseDto(user.getId());
    }

    @Transactional
    public void removeUser(String email) {
//        // 유저 유효성 검사
//        User user = this.userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("Not found user"));
//
//        // 사용자부터 먼저 삭제 처리
//        LocalDateTime now = LocalDateTime.now();
//        user.setDeletedAt(now);
//
//        // 연관된 포스트, 해당 포스트의 댓글, 집계 테이블 삭제
//        List<Post> postList = this.postRepository.findAllByUser(user);
//        postList.forEach(post ->
//                post.setDeletedAt(now)
//        );
//        List<Comment> commentListWithPost = this.commentRepository.findByPostIn(postList);
//        commentListWithPost.forEach(comment -> comment.setDeletedAt(now));
//
//
//        // 연관된 댓글 삭제 처리
//        List<Comment> commentListWithUser = this.commentRepository.findByUser(user);
//        commentListWithUser.forEach(comment -> comment.setDeletedAt(now));
//
//        //
//
//
//    }
    }
}
