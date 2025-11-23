package Capstone.CSmart.global.web.controller;

import Capstone.CSmart.global.apiPayload.ApiResponse;
import Capstone.CSmart.global.apiPayload.code.status.SuccessStatus;
import Capstone.CSmart.global.domain.entity.Admin;
import Capstone.CSmart.global.domain.entity.Student;
import Capstone.CSmart.global.domain.entity.Teacher;
import Capstone.CSmart.global.repository.AdminRepository;
import Capstone.CSmart.global.repository.StudentRepository;
import Capstone.CSmart.global.repository.TeacherRepository;
import Capstone.CSmart.global.security.handler.annotation.AuthUser;
import Capstone.CSmart.global.web.dto.Student.StudentDTO;
import Capstone.CSmart.global.web.dto.Teacher.CreateTeacherRequestDTO;
import Capstone.CSmart.global.web.dto.Teacher.TeacherDTO;
import Capstone.CSmart.global.web.dto.Teacher.UpdateTeacherRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "선생님 관리 API", description = "선생님 CRUD 관리")
@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
@Slf4j
public class TeacherController {

    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "전체 선생님 목록 조회", description = "활성 상태인 모든 선생님 목록 조회")
    @GetMapping("/")
    public ApiResponse<List<TeacherDTO>> getAllTeachers() {
        
        log.info("Get all teachers request");
        
        try {
            List<Teacher> teachers = teacherRepository.findByStatus("ACTIVE");
            
            List<TeacherDTO> teacherDTOs = teachers.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ApiResponse.onSuccess(SuccessStatus.TEACHER_OK, teacherDTOs);
            
        } catch (Exception e) {
            log.error("Failed to get all teachers", e);
            return ApiResponse.onFailure("GET_TEACHERS_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "선생님 생성", description = "새로운 선생님 계정 생성 (Admin만 가능)")
    @PostMapping("/")
    public ApiResponse<TeacherDTO> createTeacher(
            @AuthUser Long adminId,
            @Valid @RequestBody CreateTeacherRequestDTO request) {
        
        log.info("Create teacher request: name={}, email={}, kakaoId={}", 
            request.getName(), request.getEmail(), request.getKakaoId());
        
        try {
            // Admin 존재 확인
            Admin admin = adminRepository.findById(adminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found: " + adminId));
            
            // 카카오 계정 중복 확인
            if (teacherRepository.findByKakaoId(request.getKakaoId()).isPresent()) {
                return ApiResponse.onFailure("KAKAO_ID_ALREADY_EXISTS", 
                    "이미 사용 중인 카카오 계정 아이디입니다.", null);
            }
            
            // 비밀번호 암호화
            String encodedPassword = passwordEncoder.encode(request.getPassword());
            String encodedKakaoPassword = passwordEncoder.encode(request.getKakaoPassword());
            
            Teacher teacher = Teacher.builder()
                    .name(request.getName())
                    .email(request.getEmail())
                    .password(encodedPassword)
                    .phoneNumber(request.getPhoneNumber())
                    .kakaoChannelId(request.getKakaoChannelId())
                    .specialization(request.getSpecialization())
                    .kakaoId(request.getKakaoId())
                    .kakaoPasswordEncrypted(encodedKakaoPassword)
                    .createdByAdminId(adminId) // Admin과 연결
                    .status("ACTIVE")
                    .build();
            
            Teacher savedTeacher = teacherRepository.save(teacher);
            TeacherDTO teacherDTO = convertToDTO(savedTeacher);
            
            log.info("Teacher created successfully: teacherId={}, createdByAdminId={}", 
                savedTeacher.getTeacherId(), adminId);
            
            return ApiResponse.onSuccess(SuccessStatus.TEACHER_OK, teacherDTO);
            
        } catch (Exception e) {
            log.error("Failed to create teacher", e);
            return ApiResponse.onFailure("CREATE_TEACHER_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "선생님 조회", description = "특정 선생님 정보 조회")
    @GetMapping("/{id}")
    public ApiResponse<TeacherDTO> getTeacher(@PathVariable Long id) {
        
        log.info("Get teacher request: id={}", id);
        
        try {
            Teacher teacher = teacherRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Teacher not found: " + id));
            
            TeacherDTO teacherDTO = convertToDTO(teacher);
            return ApiResponse.onSuccess(SuccessStatus.TEACHER_OK, teacherDTO);
            
        } catch (Exception e) {
            log.error("Failed to get teacher: {}", id, e);
            return ApiResponse.onFailure("GET_TEACHER_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "선생님 정보 수정", description = "선생님 정보 업데이트")
    @PutMapping("/{id}")
    public ApiResponse<TeacherDTO> updateTeacher(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTeacherRequestDTO request) {
        
        log.info("Update teacher request: id={}", id);
        
        try {
            Teacher teacher = teacherRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Teacher not found: " + id));
            
            // 필드 업데이트
            if (request.getName() != null) {
                teacher.setName(request.getName());
            }
            if (request.getEmail() != null) {
                teacher.setEmail(request.getEmail());
            }
            if (request.getPhoneNumber() != null) {
                teacher.setPhoneNumber(request.getPhoneNumber());
            }
            if (request.getKakaoChannelId() != null) {
                teacher.setKakaoChannelId(request.getKakaoChannelId());
            }
            if (request.getSpecialization() != null) {
                teacher.setSpecialization(request.getSpecialization());
            }
            if (request.getPassword() != null) {
                // 실제로는 비밀번호 암호화 필요
                teacher.setPassword(request.getPassword());
            }
            if (request.getStatus() != null) {
                teacher.setStatus(request.getStatus());
            }
            
            Teacher updatedTeacher = teacherRepository.save(teacher);
            TeacherDTO teacherDTO = convertToDTO(updatedTeacher);
            
            return ApiResponse.onSuccess(SuccessStatus.TEACHER_OK, teacherDTO);
            
        } catch (Exception e) {
            log.error("Failed to update teacher: {}", id, e);
            return ApiResponse.onFailure("UPDATE_TEACHER_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "선생님 삭제", description = "선생님 정보 삭제 (soft delete)")
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteTeacher(@PathVariable Long id) {
        
        log.info("Delete teacher request: id={}", id);
        
        try {
            Teacher teacher = teacherRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Teacher not found: " + id));
            
            // Soft delete: 상태를 INACTIVE로 변경
            teacher.setStatus("INACTIVE");
            teacherRepository.save(teacher);
            
            return ApiResponse.onSuccess(SuccessStatus.TEACHER_DELETED, "Teacher deleted successfully");
            
        } catch (Exception e) {
            log.error("Failed to delete teacher: {}", id, e);
            return ApiResponse.onFailure("DELETE_TEACHER_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "선생님의 담당 학생 목록 조회", description = "특정 선생님이 담당하는 학생 목록 조회")
    @GetMapping("/{id}/students")
    public ApiResponse<List<StudentDTO>> getTeacherStudents(@PathVariable Long id) {
        
        log.info("Get teacher students request: teacherId={}", id);
        
        try {
            // 선생님 존재 확인
            teacherRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Teacher not found: " + id));
            
            List<Student> students = studentRepository.findByAssignedTeacherId(id);
            
            List<StudentDTO> studentDTOs = students.stream()
                    .map(this::convertStudentToDTO)
                    .collect(Collectors.toList());
            
            return ApiResponse.onSuccess(SuccessStatus.TEACHER_OK, studentDTOs);
            
        } catch (Exception e) {
            log.error("Failed to get teacher students: {}", id, e);
            return ApiResponse.onFailure("GET_STUDENTS_FAILED", e.getMessage(), null);
        }
    }

    private TeacherDTO convertToDTO(Teacher teacher) {
        return TeacherDTO.builder()
                .teacherId(teacher.getTeacherId())
                .name(teacher.getName())
                .email(teacher.getEmail())
                .phoneNumber(teacher.getPhoneNumber())
                .kakaoChannelId(teacher.getKakaoChannelId())
                .specialization(teacher.getSpecialization())
                .status(teacher.getStatus())
                .build();
    }

    private StudentDTO convertStudentToDTO(Student student) {
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
}



