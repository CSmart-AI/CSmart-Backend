package Capstone.CSmart.global.web.controller;

import Capstone.CSmart.global.apiPayload.ApiResponse;
import Capstone.CSmart.global.apiPayload.code.status.SuccessStatus;
import Capstone.CSmart.global.domain.entity.AiResponse;
import Capstone.CSmart.global.service.ai.AiResponseService;
import Capstone.CSmart.global.web.dto.AiResponse.AiResponseDTO;
import Capstone.CSmart.global.web.dto.AiResponse.EditResponseRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "AI 응답 관리 API", description = "AI 응답 검수 및 전송 관리")
@RestController
@RequestMapping("/api/ai-response")
@RequiredArgsConstructor
@Slf4j
public class AiResponseController {

    private final AiResponseService aiResponseService;

    @Operation(summary = "검수 대기 응답 조회", description = "특정 선생님의 검수 대기 중인 AI 응답 목록 조회")
    @GetMapping("/pending")
    public ApiResponse<List<AiResponseDTO>> getPendingResponses(
            @RequestParam(required = false) Long teacherId) {
        
        log.info("Get pending responses request: teacherId={}", teacherId);
        
        try {
            List<AiResponse> responses;
            if (teacherId != null) {
                responses = aiResponseService.getPendingResponsesForTeacher(teacherId);
            } else {
                // 모든 검수 대기 응답 조회 (선택사항)
                responses = aiResponseService.getAllPendingResponses();
            }
            
            List<AiResponseDTO> responseDTOs = responses.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ApiResponse.onSuccess(SuccessStatus.AI_RESPONSE_OK, responseDTOs);
            
        } catch (Exception e) {
            log.error("Failed to get pending responses", e);
            return ApiResponse.onFailure("GET_PENDING_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "응답 승인 및 전송", description = "AI 응답을 승인하고 카카오톡으로 전송")
    @PostMapping("/{responseId}/approve")
    public ApiResponse<String> approveResponse(@PathVariable Long responseId) {
        
        log.info("Approve response request: responseId={}", responseId);
        
        try {
            aiResponseService.approveAndSend(responseId);
            return ApiResponse.onSuccess(SuccessStatus.AI_RESPONSE_APPROVED, "Response approved and sent");
            
        } catch (Exception e) {
            log.error("Failed to approve response: {}", responseId, e);
            return ApiResponse.onFailure("APPROVE_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "응답 수정 및 전송", description = "AI 응답을 수정하고 카카오톡으로 전송")
    @PostMapping("/{responseId}/edit")
    public ApiResponse<String> editResponse(
            @PathVariable Long responseId,
            @Valid @RequestBody EditResponseRequestDTO request) {
        
        log.info("Edit response request: responseId={}, editedContent={}", 
                responseId, request.getEditedContent().substring(0, Math.min(50, request.getEditedContent().length())));
        
        try {
            aiResponseService.editAndSend(responseId, request.getEditedContent());
            return ApiResponse.onSuccess(SuccessStatus.AI_RESPONSE_APPROVED, "Response edited and sent");
            
        } catch (Exception e) {
            log.error("Failed to edit response: {}", responseId, e);
            return ApiResponse.onFailure("EDIT_FAILED", e.getMessage(), null);
        }
    }

    private AiResponseDTO convertToDTO(AiResponse aiResponse) {
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






