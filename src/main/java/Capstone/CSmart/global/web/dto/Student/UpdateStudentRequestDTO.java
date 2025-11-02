package Capstone.CSmart.global.web.dto.Student;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStudentRequestDTO {
    private String name;
    
    @Min(value = 1, message = "나이는 1 이상이어야 합니다.")
    private Integer age;
    
    private String previousSchool;
    private String targetUniversity;
    private String phoneNumber;
    private String registrationStatus;
}




