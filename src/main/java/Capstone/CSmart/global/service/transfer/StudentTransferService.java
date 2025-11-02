package Capstone.CSmart.global.service.transfer;

import Capstone.CSmart.global.domain.entity.Student;
import Capstone.CSmart.global.domain.entity.Teacher;
import Capstone.CSmart.global.repository.StudentRepository;
import Capstone.CSmart.global.repository.TeacherRepository;
import Capstone.CSmart.global.service.gemini.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentTransferService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final GeminiService geminiService;

    @Transactional
    public Student transferToTeacherChannel(Long studentId, Long teacherId) {
        try {
            // 학생과 선생님 존재 확인
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));
            
            Teacher teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Teacher not found: " + teacherId));

            // Gemini API로 채팅 기록 분석하여 사용자 정보 추출
            Map<String, Object> userInfo = geminiService.extractUserInfoFromChat(studentId);
            
            log.info("Gemini에서 추출한 사용자 정보: {}", userInfo);

            // Student 엔티티 업데이트
            if (userInfo.containsKey("name")) {
                student.setName((String) userInfo.get("name"));
            }
            if (userInfo.containsKey("age")) {
                Object age = userInfo.get("age");
                if (age instanceof Number) {
                    student.setAge(((Number) age).intValue());
                }
            }
            if (userInfo.containsKey("previousSchool")) {
                student.setPreviousSchool((String) userInfo.get("previousSchool"));
            }
            if (userInfo.containsKey("targetUniversity")) {
                student.setTargetUniversity((String) userInfo.get("targetUniversity"));
            }
            if (userInfo.containsKey("phoneNumber")) {
                student.setPhoneNumber((String) userInfo.get("phoneNumber"));
            }

            // 선생님 배정 및 상태 업데이트
            student.setAssignedTeacherId(teacherId);
            student.setRegistrationStatus("TRANSFERRED_TO_TEACHER");

            // Gemini에서 추출한 정보를 JSON으로 저장
            if (!userInfo.isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    student.setConsultationFormDataJson(mapper.writeValueAsString(userInfo));
                } catch (Exception e) {
                    log.warn("사용자 정보 JSON 저장 실패", e);
                }
            }

            Student savedStudent = studentRepository.save(student);
            log.info("Student {} transferred to Teacher {}", studentId, teacherId);
            
            return savedStudent;

        } catch (Exception e) {
            log.error("Student transfer failed", e);
            throw new RuntimeException("Student transfer failed: " + e.getMessage());
        }
    }
}






