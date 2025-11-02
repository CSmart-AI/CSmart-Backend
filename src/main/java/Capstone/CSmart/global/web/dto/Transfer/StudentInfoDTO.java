package Capstone.CSmart.global.web.dto.Transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentInfoDTO {

    private Long studentId;
    private String name;
    private Integer age;
    private String targetUniversity;
    private Long assignedTeacherId;
    private String registrationStatus;
}






