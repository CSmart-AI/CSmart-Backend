package Capstone.CSmart.global.web.Auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class AuthRequestDTO {

    @Getter
    @NoArgsConstructor
    @Schema(description = "회원가입 요청 DTO")
    public static class SignUpRequest {
        
        @NotBlank(message = "이메일은 필수 입력 값입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Schema(description = "이메일", example = "user@example.com")
        private String email;
        
        @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
        @Schema(description = "비밀번호", example = "password123")
        private String password;
        
        @NotBlank(message = "이름은 필수 입력 값입니다.")
        @Schema(description = "이름", example = "홍길동")
        private String name;
        
        @Schema(description = "닉네임", example = "홍길동123")
        private String nickname;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "로그인 요청 DTO")
    public static class LoginRequest {
        
        @NotBlank(message = "이메일은 필수 입력 값입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Schema(description = "이메일", example = "user@example.com")
        private String email;
        
        @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
        @Schema(description = "비밀번호", example = "password123")
        private String password;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "토큰 재발급 요청 DTO")
    public static class RefreshTokenRequest {
        
        @NotBlank(message = "리프레시 토큰은 필수 입력 값입니다.")
        @Schema(description = "리프레시 토큰")
        private String refreshToken;
    }
}
