package Capstone.CSmart.global.web.dto.Transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 선생님 채널 전환 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferToTeacherResponseDTO {

    /**
     * 학생 ID
     */
    private Long studentId;
    
    /**
     * 배정된 선생님 ID
     */
    private Long assignedTeacherId;
    
    /**
     * 선생님 이름
     */
    private String teacherName;
    
    /**
     * 학생 이름
     */
    private String studentName;
    
    /**
     * Gemini가 추출한 학생 정보
     */
    private ExtractedStudentInfo extractedInfo;
    
    /**
     * 저장된 대화 메시지 수
     */
    private Integer savedMessageCount;
    
    /**
     * 전환 상태
     */
    private String transferStatus;

    /**
     * Gemini가 추출한 학생 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedStudentInfo {
        private String name;
        private Integer age;
        private String previousSchool;
        private String targetUniversity;
        private String phoneNumber;
        private String major;               // 전공
        private String currentGrade;        // 현재 학년
        private String desiredSemester;     // 희망 입학 학기
        private Map<String, Object> additionalInfo;  // 기타 추출된 정보
    }
}


