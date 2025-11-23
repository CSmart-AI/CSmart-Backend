package Capstone.CSmart.global.web.controller;

import Capstone.CSmart.global.apiPayload.ApiResponse;
import Capstone.CSmart.global.apiPayload.code.status.SuccessStatus;
import Capstone.CSmart.global.security.handler.annotation.AuthUser;
import Capstone.CSmart.global.service.auth.KakaoAuthService;
import Capstone.CSmart.global.web.Auth.AuthRequestDTO;
import Capstone.CSmart.global.web.Auth.AuthResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증 API", description = "카카오 로그인, 로그아웃, 토큰 재발급 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	private final KakaoAuthService kakaoAuthService;

	@Operation(summary = "카카오 로그인", description = "카카오 계정 아이디와 비밀번호로 로그인합니다.")
	@PostMapping("/kakao-login")
	public ApiResponse<AuthResponseDTO.KakaoLoginResponse> kakaoLogin(
		@Valid @RequestBody AuthRequestDTO.KakaoLoginRequest request) {
		AuthResponseDTO.KakaoLoginResponse response = kakaoAuthService.kakaoLogin(
			request.getKakaoId(), 
			request.getKakaoPassword()
		);
		return ApiResponse.onSuccess(SuccessStatus.AUTH_OK, response);
	}

	@Operation(summary = "토큰 재발급", description = "리프레시 토큰으로 새로운 액세스 토큰을 발급받습니다.")
	@PostMapping("/refresh")
	public ApiResponse<AuthResponseDTO.RefreshTokenResponse> refreshToken(
		@Valid @RequestBody AuthRequestDTO.RefreshTokenRequest request) {
		// TODO: Admin/Teacher용 refreshToken 로직 구현 필요
		// 현재는 Member 기반이므로 추후 수정 필요
		return ApiResponse.onFailure("NOT_IMPLEMENTED", "토큰 재발급 기능은 추후 구현 예정입니다.", null);
	}

	@Operation(summary = "로그아웃", description = "로그아웃합니다.")
	@PostMapping("/logout")
	public ApiResponse<String> logout(@AuthUser Long userId) {
		// TODO: 웹훅 서버 로그아웃 연동 필요
		return ApiResponse.onSuccess(SuccessStatus.AUTH_OK, "로그아웃 되었습니다.");
	}
}

