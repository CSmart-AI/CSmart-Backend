package Capstone.CSmart.global.service.transfer;

import Capstone.CSmart.global.domain.entity.Student;
import Capstone.CSmart.global.domain.entity.Teacher;
import Capstone.CSmart.global.repository.StudentRepository;
import Capstone.CSmart.global.repository.TeacherRepository;
import Capstone.CSmart.global.service.gemini.GeminiService;
import Capstone.CSmart.global.web.dto.Transfer.TransferToTeacherResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentTransferService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final GeminiService geminiService;

    /**
     * 학생을 선생님 채널로 전환
     * 1. DB에서 대화 히스토리 자동 조회
     * 2. Gemini API로 학생 정보 추출
     * 3. Student 엔티티 업데이트 (선생님 배정 + 정보 저장)
     * 
     * @param studentId 학생 ID
     * @param teacherId 선생님 ID
     * @return 전환 결과 정보
     */
    @Transactional
    public TransferToTeacherResponseDTO transferToTeacherChannel(Long studentId, Long teacherId) {
        log.info("학생 전환 시작: studentId={}, teacherId={}", studentId, teacherId);
        
        try {
            // 1. 학생과 선생님 존재 확인
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("학생을 찾을 수 없습니다: " + studentId));
            
            Teacher teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("선생님을 찾을 수 없습니다: " + teacherId));

            // 2. Gemini API로 채팅 기록 분석하여 학생 정보 추출
            log.info("Gemini API로 학생 정보 추출 시작: studentId={}", studentId);
            Map<String, Object> extractedInfo = geminiService.extractUserInfoFromChat(studentId);
            log.info("Gemini에서 추출한 학생 정보: {}", extractedInfo);

            // 3. Student 엔티티 업데이트
            updateStudentInfo(student, extractedInfo);

            // 4. 선생님 배정 및 상태 업데이트
            student.setAssignedTeacherId(teacherId);
            student.setRegistrationStatus("TRANSFERRED_TO_TEACHER");

            // 5. Gemini에서 추출한 정보를 JSON으로 저장
            saveExtractedInfoAsJson(student, extractedInfo);

            // 6. 저장
            Student savedStudent = studentRepository.save(student);
            log.info("학생 전환 완료: studentId={}, teacherId={}, extractedFields={}", 
                     studentId, teacherId, extractedInfo.keySet());
            
            // 7. Response 생성
            return buildTransferResponse(savedStudent, teacher, extractedInfo);

        } catch (Exception e) {
            log.error("학생 전환 실패: studentId={}, teacherId={}, error={}", 
                     studentId, teacherId, e.getMessage(), e);
            throw new RuntimeException("학생 전환 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * Gemini가 추출한 정보로 Student 엔티티 업데이트
     */
    private void updateStudentInfo(Student student, Map<String, Object> extractedInfo) {
        if (extractedInfo.containsKey("name")) {
            student.setName((String) extractedInfo.get("name"));
        }
        if (extractedInfo.containsKey("age")) {
            Object age = extractedInfo.get("age");
            if (age instanceof Number) {
                student.setAge(((Number) age).intValue());
            }
        }
        if (extractedInfo.containsKey("previousSchool")) {
            student.setPreviousSchool((String) extractedInfo.get("previousSchool"));
        }
        if (extractedInfo.containsKey("targetUniversity")) {
            student.setTargetUniversity((String) extractedInfo.get("targetUniversity"));
        }
        if (extractedInfo.containsKey("phoneNumber")) {
            student.setPhoneNumber((String) extractedInfo.get("phoneNumber"));
        }
        if (extractedInfo.containsKey("desiredMajor")) {
            student.setDesiredMajor((String) extractedInfo.get("desiredMajor"));
        }
        if (extractedInfo.containsKey("currentGrade")) {
            student.setCurrentGrade((String) extractedInfo.get("currentGrade"));
        }
        if (extractedInfo.containsKey("desiredSemester")) {
            student.setDesiredSemester((String) extractedInfo.get("desiredSemester"));
        }
    }
    
    /**
     * 추출된 정보를 JSON으로 저장
     */
    private void saveExtractedInfoAsJson(Student student, Map<String, Object> extractedInfo) {
        if (!extractedInfo.isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                student.setConsultationFormDataJson(mapper.writeValueAsString(extractedInfo));
            } catch (Exception e) {
                log.warn("학생 정보 JSON 저장 실패: studentId={}", student.getStudentId(), e);
            }
        }
    }
    
    /**
     * 전환 결과 Response 생성
     */
    private TransferToTeacherResponseDTO buildTransferResponse(
            Student student, Teacher teacher, Map<String, Object> extractedInfo) {
        
        // ExtractedStudentInfo 생성
        TransferToTeacherResponseDTO.ExtractedStudentInfo studentInfo = 
            TransferToTeacherResponseDTO.ExtractedStudentInfo.builder()
                .name((String) extractedInfo.get("name"))
                .age(extractedInfo.get("age") instanceof Number ? 
                     ((Number) extractedInfo.get("age")).intValue() : null)
                .previousSchool((String) extractedInfo.get("previousSchool"))
                .targetUniversity((String) extractedInfo.get("targetUniversity"))
                .phoneNumber((String) extractedInfo.get("phoneNumber"))
                .major((String) extractedInfo.get("major"))
                .currentGrade((String) extractedInfo.get("currentGrade"))
                .desiredSemester((String) extractedInfo.get("desiredSemester"))
                .additionalInfo(new HashMap<>(extractedInfo))
                .build();
        
        return TransferToTeacherResponseDTO.builder()
                .studentId(student.getStudentId())
                .assignedTeacherId(teacher.getTeacherId())
                .teacherName(teacher.getName())
                .studentName(student.getName())
                .extractedInfo(studentInfo)
                .savedMessageCount(extractedInfo.size())
                .transferStatus("SUCCESS")
                .build();
    }
}











