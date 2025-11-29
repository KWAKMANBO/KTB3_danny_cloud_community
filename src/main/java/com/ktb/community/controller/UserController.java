package com.ktb.community.controller;

import com.ktb.community.dto.request.ChangePasswordRequestDto;
import com.ktb.community.dto.request.EmailCheckRequestDto;
import com.ktb.community.dto.request.ModifyNicknameRequestDto;
import com.ktb.community.dto.request.PasswordCheckRequestDto;
import com.ktb.community.dto.request.UpdateProfileImageRequestDto;
import com.ktb.community.dto.response.ApiResponseDto;
import com.ktb.community.dto.response.AvailabilityResponseDto;
import com.ktb.community.dto.response.CrudUserResponseDto;
import com.ktb.community.dto.response.UserInfoResponseDto;
import com.ktb.community.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }


    @PostMapping("/email")
    ResponseEntity<ApiResponseDto<AvailabilityResponseDto>> checkEmail(@RequestBody @Valid EmailCheckRequestDto emailCheckDto, BindingResult bindingResult) {
        // 검증에서 문제가 발생했다면
        if (bindingResult.hasErrors()) {
            String message = (bindingResult.getFieldError("email") != null) ? bindingResult.getFieldError("email").getDefaultMessage() : "Not a valid request";
            return ResponseEntity.badRequest().body(ApiResponseDto.error(message));
        }

        AvailabilityResponseDto availabilityResponseDto = this.userService.checkDuplicateEmail(emailCheckDto.getEmail());
        return ResponseEntity.ok().body(ApiResponseDto.success(availabilityResponseDto));
    }


    @PostMapping("/password")
    ResponseEntity<ApiResponseDto<AvailabilityResponseDto>> checkValidityPassword(@RequestBody @Valid PasswordCheckRequestDto passwordCheckRequestDto, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String message = (bindingResult.getFieldError("password") != null)
                    ? bindingResult.getFieldError("password").getDefaultMessage()
                    : "Not a valid request";
            return ResponseEntity.badRequest().body(ApiResponseDto.error(message));
        }

        AvailabilityResponseDto availabilityResponseDto = this.userService.checkValidityPassword(passwordCheckRequestDto.getPassword());
        return ResponseEntity.ok().body(ApiResponseDto.success(availabilityResponseDto));
    }

    @PatchMapping("/password")
    public ResponseEntity<ApiResponseDto<?>> changePassword(
            @RequestBody @Valid ChangePasswordRequestDto changePasswordRequestDto,
            BindingResult bindingResult,
            Authentication authentication) {
        if (bindingResult.hasErrors()) {
            String message = bindingResult.getFieldError() != null
                    ? bindingResult.getFieldError().getDefaultMessage()
                    : "Not a valid request";
            return ResponseEntity.badRequest().body(ApiResponseDto.error(message));
        }

        String email = authentication.getName();
        CrudUserResponseDto crudUserResponseDto = this.userService.changePassword(email, changePasswordRequestDto);

        return ResponseEntity.ok().body(ApiResponseDto.success(crudUserResponseDto));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<?>> getMyInformation(Authentication authentication) {
        String email = authentication.getName();

        UserInfoResponseDto userInfoResponseDto = this.userService.readMyInfo(email);
        return ResponseEntity.ok().body(ApiResponseDto.success(userInfoResponseDto));

    }

    @PatchMapping("/nickname")
    public ResponseEntity<ApiResponseDto<?>> patchNickname(
            @RequestBody @Valid ModifyNicknameRequestDto modifyNicknameRequestDto,
            Authentication authentication) {
        String email = authentication.getName();
        CrudUserResponseDto crudUserResponseDto = this.userService.changeNickname(email, modifyNicknameRequestDto);

        return ResponseEntity.ok().body(ApiResponseDto.success(crudUserResponseDto));

    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponseDto<?>> deleteUser(Authentication authentication){
        String email = authentication.getName();
        //TODO : 삭제 로직 구현하기
        this.userService.removeUser(email);
        return ResponseEntity.ok().body(ApiResponseDto.success("test중"));
    }

    @PatchMapping("/profile-image")
    public ResponseEntity<ApiResponseDto<CrudUserResponseDto>> updateProfileImage(
            @RequestBody @Valid UpdateProfileImageRequestDto dto,
            Authentication authentication
    ) {
        String email = authentication.getName();
        CrudUserResponseDto response = userService.updateProfileImage(email, dto.getImageKey());
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    @DeleteMapping("/profile-image")
    public ResponseEntity<ApiResponseDto<CrudUserResponseDto>> deleteProfileImage(
            Authentication authentication
    ) {
        String email = authentication.getName();
        CrudUserResponseDto response = userService.deleteProfileImage(email);
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }
}
