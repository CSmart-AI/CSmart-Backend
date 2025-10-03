package Capstone.CSmart.global.web.dto.Auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class AuthResponseDTO {

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@Schema(description = "회원가입 응답 DTO")
	public static class SignUpResponse {

		@Schema(description = "회원 ID")
		private Long memberId;

		@Schema(description = "이메일")
		private String email;

		@Schema(description = "이름")
		private String name;

		@Schema(description = "닉네임")
		private String nickname;

		@Schema(description = "가입 일시")
		private LocalDateTime createdAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@Schema(description = "로그인 응답 DTO")
	public static class LoginResponse {

		@Schema(description = "회원 ID")
		private Long memberId;

		@Schema(description = "이메일")
		private String email;

		@Schema(description = "이름")
		private String name;

		@Schema(description = "액세스 토큰")
		private String accessToken;

		@Schema(description = "리프레시 토큰")
		private String refreshToken;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@Schema(description = "토큰 재발급 응답 DTO")
	public static class RefreshTokenResponse {

		@Schema(description = "새로운 액세스 토큰")
		private String accessToken;

		@Schema(description = "새로운 리프레시 토큰")
		private String refreshToken;
	}
}
