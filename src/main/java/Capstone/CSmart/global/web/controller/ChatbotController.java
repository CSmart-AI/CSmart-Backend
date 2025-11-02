package Capstone.CSmart.global.web.controller;

import Capstone.CSmart.global.apiPayload.ApiResponse;
import Capstone.CSmart.global.apiPayload.code.status.SuccessStatus;
import Capstone.CSmart.global.service.ingest.MessageIngestService;
import Capstone.CSmart.global.web.dto.Kakao.ChatbotRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "카카오톡 챗봇 API", description = "카카오톡 웹훅에서 전달되는 챗봇 메시지 수신")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ChatbotController {

    private final MessageIngestService messageIngestService;

    @Operation(summary = "카카오톡 챗봇 메시지 수신", description = "카카오톡 웹훅에서 전달한 챗봇 메시지를 수신/저장하고 AI 응답을 생성합니다.")
    @PostMapping("/kakao/messages")
    public ApiResponse<String> receiveChatbotMessage(@Valid @RequestBody ChatbotRequestDTO request) {
        log.info("카카오톡 챗봇 메시지 수신: userId={}, utterance={}", request.getUserId(), request.getUtterance());
        
        try {
            // MessageIngestService로 메시지 처리 (메시지 저장 + AI 응답 생성)
            messageIngestService.ingestAndGenerateResponse(request);
            
            log.info("카카오톡 챗봇 메시지 처리 완료: userId={}", request.getUserId());
            return ApiResponse.onSuccess(SuccessStatus.CHATBOT_MESSAGE_RECEIVED, "received");
            
        } catch (Exception e) {
            log.error("카카오톡 챗봇 메시지 처리 중 오류 발생: userId={}", request.getUserId(), e);
            return ApiResponse.onFailure("PROCESSING_FAILED", e.getMessage(), null);
        }
    }
}
