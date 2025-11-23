package Capstone.CSmart.global.web.controller;

import Capstone.CSmart.global.apiPayload.ApiResponse;
import Capstone.CSmart.global.apiPayload.code.status.SuccessStatus;
import Capstone.CSmart.global.security.handler.annotation.AuthUser;
import Capstone.CSmart.global.service.transfer.StudentTransferService;
import Capstone.CSmart.global.web.dto.Transfer.TransferToTeacherRequestDTO;
import Capstone.CSmart.global.web.dto.Transfer.TransferToTeacherResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 학생 전환 API
 * Admin이 학생을 선생님 채널로 전환하는 API
 */
@Tag(name = "학생 전환 API", description = "학생을 선생님 채널로 전환하는 API")
@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
@Slf4j
public class StudentTransferController {

    private final StudentTransferService studentTransferService;

    /**
     * 학생을 선생님 채널로 전환
     * 
     * 처리 프로세스:
     * 1. DB에서 학생의 대화 히스토리 조회
     * 2. Gemini API로 학생 정보 추출 (이름, 나이, 출신학교, 목표대학 등)
     * 3. Student 엔티티 업데이트 (추출된 정보 + 선생님 배정)
     * 4. 모든 작업을 하나의 트랜잭션으로 처리
     * 
     * @param adminId 요청한 Admin ID (JWT에서 추출)
     * @param request 전환 요청 정보 (studentId, teacherId)
     * @return 전환 결과 정보
     */
    @Operation(
        summary = "학생을 선생님 채널로 전환",
        description = "Admin이 학생을 선생님 채널로 전환합니다. " +
                      "Gemini API로 대화 히스토리를 분석하여 학생 정보를 자동 추출하고, " +
                      "선생님을 배정합니다. 모든 작업은 단일 트랜잭션으로 처리됩니다."
    )
    @PostMapping("/to-teacher")
    public ApiResponse<TransferToTeacherResponseDTO> transferToTeacher(
            @AuthUser Long adminId,
            @Valid @RequestBody TransferToTeacherRequestDTO request
    ) {
        log.info("학생 전환 요청: adminId={}, studentId={}, teacherId={}", 
                 adminId, request.getStudentId(), request.getTeacherId());

        // 학생 전환 실행
        TransferToTeacherResponseDTO response = studentTransferService.transferToTeacherChannel(
                request.getStudentId(),
                request.getTeacherId()
        );

        log.info("학생 전환 성공: adminId={}, studentId={}, teacherId={}, extractedFields={}", 
                 adminId, request.getStudentId(), request.getTeacherId(), 
                 response.getExtractedInfo() != null ? response.getExtractedInfo().getAdditionalInfo().keySet() : "[]");

        return ApiResponse.onSuccess(SuccessStatus.CHATBOT_INFO_EXTRACTED, response);
    }
}
