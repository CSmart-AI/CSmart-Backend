package Capstone.CSmart.global.web.controller;

import Capstone.CSmart.global.apiPayload.ApiResponse;
import Capstone.CSmart.global.apiPayload.code.status.SuccessStatus;
import Capstone.CSmart.global.domain.enums.ChannelType;
import Capstone.CSmart.global.service.kakao.KakaoWebhookService;
import Capstone.CSmart.global.web.dto.Kakao.KakaoMessageDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 카카오톡 웹훅 컨트롤러
 * Admin 채널과 Teacher 채널의 메시지를 각각 수신하는 엔드포인트 제공
 */
@Tag(name = "카카오톡 웹훅 API", description = "카카오톡 채널별 메시지 수신 API")
@RestController
@RequestMapping("/api/kakao/webhook")
@RequiredArgsConstructor
@Slf4j
public class KakaoWebhookController {

    private final KakaoWebhookService kakaoWebhookService;

    /**
     * Admin 채널 웹훅 엔드포인트
     * Admin 채널에서 수신한 메시지를 처리합니다.
     * 
     * @param message 카카오톡 메시지 DTO
     * @return API 응답
     */
    @Operation(
        summary = "Admin 채널 메시지 수신",
        description = "Admin 카카오톡 채널에서 수신한 메시지를 처리합니다. 기본 상담 응답을 생성합니다."
    )
    @PostMapping("/admin")
    public ApiResponse<String> handleAdminChannelMessage(@RequestBody KakaoMessageDTO message) {
        log.info("Admin 채널 메시지 수신: userId={}, botId={}, message={}", 
                 message.getUserId(), message.getBotId(), message.getUtterance());
        
        // ChannelType 명시적으로 설정
        message.setChannelType(ChannelType.ADMIN);
        
        // 메시지 처리 (비동기)
        kakaoWebhookService.handleIncomingMessage(message);
        
        log.info("Admin 채널 메시지 처리 완료: userId={}", message.getUserId());
        return ApiResponse.onSuccess(SuccessStatus.CHATBOT_MESSAGE_RECEIVED, "received");
    }

    /**
     * Teacher 채널 웹훅 엔드포인트
     * Teacher 채널에서 수신한 메시지를 처리합니다.
     * 
     * @param message 카카오톡 메시지 DTO
     * @return API 응답
     */
    @Operation(
        summary = "Teacher 채널 메시지 수신",
        description = "Teacher 카카오톡 채널에서 수신한 메시지를 처리합니다. 심화 상담 응답을 생성합니다."
    )
    @PostMapping("/teacher")
    public ApiResponse<String> handleTeacherChannelMessage(@RequestBody KakaoMessageDTO message) {
        log.info("Teacher 채널 메시지 수신: userId={}, botId={}, message={}", 
                 message.getUserId(), message.getBotId(), message.getUtterance());
        
        // ChannelType 명시적으로 설정
        message.setChannelType(ChannelType.TEACHER);
        
        // 메시지 처리 (비동기)
        kakaoWebhookService.handleIncomingMessage(message);
        
        log.info("Teacher 채널 메시지 처리 완료: userId={}", message.getUserId());
        return ApiResponse.onSuccess(SuccessStatus.CHATBOT_MESSAGE_RECEIVED, "received");
    }
}


