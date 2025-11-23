package Capstone.CSmart.global.web.dto.Student;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDTO {
    private Long studentId;
    private String name;
    private Integer age;
    private String previousSchool;
    private String targetUniversity;
    private String phoneNumber;
    private String kakaoChannelId;
    private String kakaoUserId;
    private Long assignedTeacherId;
    private String registrationStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
