package Capstone.CSmart.global.service.transfer;

import Capstone.CSmart.global.domain.entity.Student;
import Capstone.CSmart.global.domain.entity.Teacher;
import Capstone.CSmart.global.repository.StudentRepository;
import Capstone.CSmart.global.repository.TeacherRepository;
import Capstone.CSmart.global.service.gemini.GeminiService;
import Capstone.CSmart.global.service.student.StudentInfoUpdateService;
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
    private final StudentInfoUpdateService studentInfoUpdateService;

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
            
            if (extractedInfo == null || extractedInfo.isEmpty()) {
                log.warn("Gemini API에서 학생 정보 추출 실패 또는 빈 결과: studentId={}. 기존 정보로 진행합니다.", studentId);
                extractedInfo = new HashMap<>();
            } else {
                log.info("Gemini에서 추출한 학생 정보: studentId={}, 필드 수={}, 필드명={}", 
                        studentId, extractedInfo.size(), extractedInfo.keySet());
            }

            // 3. 공통 서비스를 사용하여 Student 엔티티 업데이트
            // extractedInfo가 비어있어도 기존 정보는 유지되고 선생님만 배정됨
            student = studentInfoUpdateService.updateStudentInfo(student, extractedInfo);

            // 4. 선생님 배정 및 상태 업데이트
            student.setAssignedTeacherId(teacherId);
            student.setRegistrationStatus("TRANSFERRED_TO_TEACHER");

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
     * 전환 결과 Response 생성
     */
    private TransferToTeacherResponseDTO buildTransferResponse(
            Student student, Teacher teacher, Map<String, Object> extractedInfo) {
        
        // extractedInfo가 null이면 빈 Map으로 처리
        if (extractedInfo == null) {
            extractedInfo = new HashMap<>();
        }
        
        // ExtractedStudentInfo 생성 (null-safe)
        TransferToTeacherResponseDTO.ExtractedStudentInfo studentInfo = 
            TransferToTeacherResponseDTO.ExtractedStudentInfo.builder()
                .name(extractedInfo.containsKey("name") ? (String) extractedInfo.get("name") : null)
                .age(extractedInfo.containsKey("age") && extractedInfo.get("age") instanceof Number ? 
                     ((Number) extractedInfo.get("age")).intValue() : null)
                .previousSchool(extractedInfo.containsKey("previousSchool") ? 
                               (String) extractedInfo.get("previousSchool") : null)
                .targetUniversity(extractedInfo.containsKey("targetUniversity") ? 
                                (String) extractedInfo.get("targetUniversity") : null)
                .phoneNumber(extractedInfo.containsKey("phoneNumber") ? 
                           (String) extractedInfo.get("phoneNumber") : null)
                .major(extractedInfo.containsKey("desiredMajor") ? 
                      (String) extractedInfo.get("desiredMajor") : null)
                .currentGrade(extractedInfo.containsKey("currentGrade") ? 
                            (String) extractedInfo.get("currentGrade") : null)
                .desiredSemester(extractedInfo.containsKey("desiredSemester") ? 
                               (String) extractedInfo.get("desiredSemester") : null)
                .additionalInfo(new HashMap<>(extractedInfo))
                .build();
        
        return TransferToTeacherResponseDTO.builder()
                .studentId(student.getStudentId())
                .assignedTeacherId(teacher.getTeacherId())
                .teacherName(teacher.getName())
                .studentName(student.getName())
                .extractedInfo(studentInfo)
                .savedMessageCount(1)
                .transferStatus("SUCCESS")
                .build();
    }
}











