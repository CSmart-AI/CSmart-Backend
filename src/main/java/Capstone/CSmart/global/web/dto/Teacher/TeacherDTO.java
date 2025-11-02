package Capstone.CSmart.global.web.dto.Teacher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherDTO {

    private Long teacherId;
    private String name;
    private String email;
    private String phoneNumber;
    private String kakaoChannelId;
    private String specialization;
    private String status;
}






