package Capstone.CSmart.global.web.dto.Teacher;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTeacherRequestDTO {
    private String name;
    
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;
    
    private String phoneNumber;
    private String kakaoChannelId;
    private String specialization;
    private String password;
    private String status;
}




