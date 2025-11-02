package Capstone.CSmart.global.web.controller;

import Capstone.CSmart.global.apiPayload.ApiResponse;
import Capstone.CSmart.global.apiPayload.code.status.SuccessStatus;
import Capstone.CSmart.global.domain.entity.Student;
import Capstone.CSmart.global.service.transfer.StudentTransferService;
import Capstone.CSmart.global.web.dto.Transfer.TransferToTeacherRequestDTO;
import Capstone.CSmart.global.web.dto.Transfer.StudentInfoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "학생 배정 API", description = "상담 채널에서 선생님 채널로 학생 배정")
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@Slf4j
public class StudentTransferController {

    private final StudentTransferService studentTransferService;

    @Operation(summary = "선생님 채널로 전송", description = "Gemini API로 채팅 기록 분석하여 학생 정보 추출 후 선생님에게 배정")
    @PostMapping("/transfer-to-teacher")
    public ApiResponse<StudentInfoDTO> transferToTeacher(
            @Valid @RequestBody TransferToTeacherRequestDTO request) {
        
        log.info("Student transfer request: studentId={}, teacherId={}", 
                request.getStudentId(), request.getTeacherId());
        
        try {
            Student student = studentTransferService.transferToTeacherChannel(
                    request.getStudentId(), request.getTeacherId());
            
            StudentInfoDTO studentInfo = StudentInfoDTO.builder()
                    .studentId(student.getStudentId())
                    .name(student.getName())
                    .age(student.getAge())
                    .targetUniversity(student.getTargetUniversity())
                    .assignedTeacherId(student.getAssignedTeacherId())
                    .registrationStatus(student.getRegistrationStatus())
                    .build();
            
            return ApiResponse.onSuccess(SuccessStatus.STUDENT_OK, studentInfo);
            
        } catch (Exception e) {
            log.error("Student transfer failed", e);
            return ApiResponse.onFailure("TRANSFER_FAILED", e.getMessage(), null);
        }
    }
}



