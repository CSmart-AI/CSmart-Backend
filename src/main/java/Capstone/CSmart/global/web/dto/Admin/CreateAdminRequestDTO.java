package Capstone.CSmart.global.web.dto.Admin;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAdminRequestDTO {

    @NotBlank(message = "이름은 필수 입력 값입니다.")
    private String name;

    @NotBlank(message = "카카오 계정 아이디는 필수 입력 값입니다.")
    private String kakaoId; // 카카오 계정 아이디

    @NotBlank(message = "카카오 계정 비밀번호는 필수 입력 값입니다.")
    private String kakaoPassword; // 카카오 계정 비밀번호
}

