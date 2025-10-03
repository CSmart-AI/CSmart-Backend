package Capstone.CSmart.global.web.controller;

import Capstone.CSmart.global.apiPayload.ApiResponse;
import Capstone.CSmart.global.apiPayload.code.status.SuccessStatus;
import Capstone.CSmart.global.security.handler.annotation.AuthUser;
import Capstone.CSmart.global.service.member.MemberCommandService;
import Capstone.CSmart.global.web.Auth.AuthRequestDTO;
import Capstone.CSmart.global.web.Auth.AuthResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증 API", description = "회원가입, 로그인, 로그아웃, 토큰 재발급 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	private final MemberCommandService memberCommandService;

	@Operation(summary = "회원가입", description = "새로운 회원을 등록합니다.")
	@PostMapping("/signup")
	public ApiResponse<AuthResponseDTO.SignUpResponse> signUp(
		@Valid @RequestBody AuthRequestDTO.SignUpRequest request) {
		AuthResponseDTO.SignUpResponse response = memberCommandService.signUp(request);
		return ApiResponse.onSuccess(SuccessStatus.MEMBER_OK, response);
	}

	@Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인합니다.")
	@PostMapping("/login")
	public ApiResponse<AuthResponseDTO.LoginResponse> login(
		@Valid @RequestBody AuthRequestDTO.LoginRequest request) {
		AuthResponseDTO.LoginResponse response = memberCommandService.login(request);
		return ApiResponse.onSuccess(SuccessStatus.MEMBER_OK, response);
	}

	@Operation(summary = "토큰 재발급", description = "리프레시 토큰으로 새로운 액세스 토큰을 발급받습니다.")
	@PostMapping("/refresh")
	public ApiResponse<AuthResponseDTO.RefreshTokenResponse> refreshToken(
		@Valid @RequestBody AuthRequestDTO.RefreshTokenRequest request) {
		AuthResponseDTO.RefreshTokenResponse response = memberCommandService.refreshToken(request);
		return ApiResponse.onSuccess(SuccessStatus.MEMBER_OK, response);
	}

	@Operation(summary = "로그아웃", description = "로그아웃하여 리프레시 토큰을 제거합니다.")
	@PostMapping("/logout")
	public ApiResponse<String> logout(@AuthUser Long memberId) {
		memberCommandService.logout(memberId);
		return ApiResponse.onSuccess(SuccessStatus.MEMBER_OK, "로그아웃 되었습니다.");
	}
}

