package Capstone.CSmart.global.web.controller;

import Capstone.CSmart.global.apiPayload.ApiResponse;
import Capstone.CSmart.global.apiPayload.code.status.SuccessStatus;
import Capstone.CSmart.global.domain.entity.AiResponse;
import Capstone.CSmart.global.domain.entity.Message;
import Capstone.CSmart.global.domain.entity.Student;
import Capstone.CSmart.global.domain.entity.Teacher;
import Capstone.CSmart.global.repository.AiResponseRepository;
import Capstone.CSmart.global.repository.MessageRepository;
import Capstone.CSmart.global.repository.StudentRepository;
import Capstone.CSmart.global.repository.TeacherRepository;
import Capstone.CSmart.global.web.dto.AiResponse.AiResponseDTO;
import Capstone.CSmart.global.web.dto.Student.StudentDTO;
import Capstone.CSmart.global.web.dto.Student.UpdateStudentRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "학생 관리 API", description = "학생 CRUD 및 메시지/AI응답 이력 조회")
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@Slf4j
public class StudentController {

    private final StudentRepository studentRepository;
    private final MessageRepository messageRepository;
    private final AiResponseRepository aiResponseRepository;
    private final TeacherRepository teacherRepository;

    @Operation(summary = "전체 학생 목록 조회", description = "필터링 가능한 학생 목록 조회 (담당 선생님, 등록 상태 등)")
    @GetMapping
    public ApiResponse<List<StudentDTO>> getAllStudents(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) String registrationStatus) {
        
        log.info("Get all students request: teacherId={}, registrationStatus={}", teacherId, registrationStatus);
        
        try {
            List<Student> students;
            
            if (teacherId != null && registrationStatus != null) {
                students = studentRepository.findByAssignedTeacherIdAndRegistrationStatus(teacherId, registrationStatus);
            } else if (teacherId != null) {
                students = studentRepository.findByAssignedTeacherId(teacherId);
            } else if (registrationStatus != null) {
                students = studentRepository.findByRegistrationStatus(registrationStatus);
            } else {
                students = studentRepository.findAll();
            }
            
            List<StudentDTO> studentDTOs = students.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ApiResponse.onSuccess(SuccessStatus.STUDENT_OK, studentDTOs);
            
        } catch (Exception e) {
            log.error("Failed to get all students", e);
            return ApiResponse.onFailure("GET_STUDENTS_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "학생 상세 조회", description = "특정 학생의 상세 정보 조회")
    @GetMapping("/{id}")
    public ApiResponse<StudentDTO> getStudent(@PathVariable Long id) {
        
        log.info("Get student request: id={}", id);
        
        try {
            Student student = studentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Student not found: " + id));
            
            StudentDTO studentDTO = convertToDTO(student);
            return ApiResponse.onSuccess(SuccessStatus.STUDENT_OK, studentDTO);
            
        } catch (Exception e) {
            log.error("Failed to get student: {}", id, e);
            return ApiResponse.onFailure("GET_STUDENT_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "학생의 메시지 이력 조회", description = "특정 학생의 메시지 이력 조회")
    @GetMapping("/{id}/messages")
    public ApiResponse<List<Map<String, Object>>> getStudentMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "20") int limit) {
        
        log.info("Get student messages request: studentId={}, limit={}", id, limit);
        
        try {
            // 학생 존재 확인
            studentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Student not found: " + id));
            
            List<Message> messages = messageRepository.findByStudentIdOrderBySentAtDesc(
                    id, PageRequest.of(0, limit));
            
            List<Map<String, Object>> messageDTOs = messages.stream()
                    .map(this::convertMessageToMap)
                    .collect(Collectors.toList());
            
            return ApiResponse.onSuccess(SuccessStatus.MESSAGE_OK, messageDTOs);
            
        } catch (Exception e) {
            log.error("Failed to get student messages: {}", id, e);
            return ApiResponse.onFailure("GET_MESSAGES_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "학생의 AI 응답 이력 조회", description = "특정 학생의 AI 응답 이력 조회")
    @GetMapping("/{id}/ai-responses")
    public ApiResponse<List<AiResponseDTO>> getStudentAiResponses(@PathVariable Long id) {
        
        log.info("Get student AI responses request: studentId={}", id);
        
        try {
            // 학생 존재 확인
            studentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Student not found: " + id));
            
            List<AiResponse> aiResponses = aiResponseRepository.findByStudentIdOrderByGeneratedAtDesc(id);
            
            List<AiResponseDTO> aiResponseDTOs = aiResponses.stream()
                    .map(this::convertAiResponseToDTO)
                    .collect(Collectors.toList());
            
            return ApiResponse.onSuccess(SuccessStatus.AI_RESPONSE_OK, aiResponseDTOs);
            
        } catch (Exception e) {
            log.error("Failed to get student AI responses: {}", id, e);
            return ApiResponse.onFailure("GET_AI_RESPONSES_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "학생 정보 수정", description = "학생 정보 업데이트")
    @PutMapping("/{id}")
    public ApiResponse<StudentDTO> updateStudent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStudentRequestDTO request) {
        
        log.info("Update student request: id={}", id);
        
        try {
            Student student = studentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Student not found: " + id));
            
            // 필드 업데이트
            if (request.getName() != null) {
                student.setName(request.getName());
            }
            if (request.getAge() != null) {
                student.setAge(request.getAge());
            }
            if (request.getPreviousSchool() != null) {
                student.setPreviousSchool(request.getPreviousSchool());
            }
            if (request.getTargetUniversity() != null) {
                student.setTargetUniversity(request.getTargetUniversity());
            }
            if (request.getPhoneNumber() != null) {
                student.setPhoneNumber(request.getPhoneNumber());
            }
            if (request.getRegistrationStatus() != null) {
                student.setRegistrationStatus(request.getRegistrationStatus());
            }
            
            Student updatedStudent = studentRepository.save(student);
            StudentDTO studentDTO = convertToDTO(updatedStudent);
            
            return ApiResponse.onSuccess(SuccessStatus.STUDENT_OK, studentDTO);
            
        } catch (Exception e) {
            log.error("Failed to update student: {}", id, e);
            return ApiResponse.onFailure("UPDATE_STUDENT_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "학생 삭제", description = "학생 정보 삭제 (soft delete)")
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteStudent(@PathVariable Long id) {
        
        log.info("Delete student request: id={}", id);
        
        try {
            Student student = studentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Student not found: " + id));
            
            // Soft delete: 상태를 DELETED로 변경
            student.setRegistrationStatus("DELETED");
            studentRepository.save(student);
            
            return ApiResponse.onSuccess(SuccessStatus.STUDENT_DELETED, "Student deleted successfully");
            
        } catch (Exception e) {
            log.error("Failed to delete student: {}", id, e);
            return ApiResponse.onFailure("DELETE_STUDENT_FAILED", e.getMessage(), null);
        }
    }

    // DTO 변환 메서드들
    private StudentDTO convertToDTO(Student student) {
        return StudentDTO.builder()
                .studentId(student.getStudentId())
                .name(student.getName())
                .age(student.getAge())
                .previousSchool(student.getPreviousSchool())
                .targetUniversity(student.getTargetUniversity())
                .phoneNumber(student.getPhoneNumber())
                .kakaoChannelId(student.getKakaoChannelId())
                .kakaoUserId(student.getKakaoUserId())
                .assignedTeacherId(student.getAssignedTeacherId())
                .registrationStatus(student.getRegistrationStatus())
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
                .build();
    }

    private Map<String, Object> convertMessageToMap(Message message) {
        Map<String, Object> map = new HashMap<>();
        map.put("messageId", message.getMessageId());
        map.put("content", message.getContent());
        map.put("messageType", message.getMessageType());
        map.put("senderType", message.getSenderType());
        map.put("sentAt", message.getSentAt());
        map.put("teacherId", message.getTeacherId());
        return map;
    }

    private AiResponseDTO convertAiResponseToDTO(AiResponse aiResponse) {
        return AiResponseDTO.builder()
                .responseId(aiResponse.getResponseId())
                .messageId(aiResponse.getMessageId())
                .studentId(aiResponse.getStudentId())
                .teacherId(aiResponse.getTeacherId())
                .recommendedResponse(aiResponse.getRecommendedResponse())
                .status(aiResponse.getStatus().toString())
                .generatedAt(aiResponse.getGeneratedAt())
                .build();
    }
}

