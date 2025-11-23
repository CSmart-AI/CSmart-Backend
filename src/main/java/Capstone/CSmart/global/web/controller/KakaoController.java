package Capstone.CSmart.global.web.controller;

import Capstone.CSmart.global.apiPayload.ApiResponse;
import Capstone.CSmart.global.apiPayload.code.status.SuccessStatus;
import Capstone.CSmart.global.domain.enums.ChannelType;
import Capstone.CSmart.global.service.kakao.KakaoService;
import Capstone.CSmart.global.web.dto.Kakao.KakaoRequestDTO;
import Capstone.CSmart.global.web.dto.Kakao.KakaoResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "카카오톡 API", description = "카카오톡 메시지 전송 및 채팅 목록 조회 API")
@RestController
@RequestMapping("/kakao")
@RequiredArgsConstructor
@Slf4j
public class KakaoController {

    private final KakaoService kakaoService;

    @Operation(
        summary = "카카오톡 메시지 전송",
        description = "카카오톡으로 메시지를 전송합니다. channelType: ADMIN 또는 TEACHER"
    )
    @PostMapping("/send-message")
    public ApiResponse<KakaoResponseDTO.SendMessageResponse> sendMessage(
        @Valid @RequestBody KakaoRequestDTO.SendMessageRequest request,
        @RequestParam(required = false, defaultValue = "ADMIN") ChannelType channelType
    ) {
        try {
            log.info("카카오톡 메시지 전송 요청: recipient={}, channel={}", request.getRecipient(), channelType);
            
            KakaoResponseDTO.SendMessageResponse response = kakaoService.sendMessage(request, channelType);
            
            log.info("카카오톡 메시지 전송 성공");
            return ApiResponse.onSuccess(SuccessStatus.KAKAO_OK, response);
            
        } catch (Exception e) {
            log.error("카카오톡 메시지 전송 실패", e);
            throw e;
        }
    }

    @Operation(
        summary = "카카오톡 채팅 목록 조회",
        description = "카카오톡에서 채팅 목록을 조회합니다. channelType: ADMIN 또는 TEACHER"
    )
    @GetMapping("/chat-list")
    public ApiResponse<KakaoResponseDTO.ChatListResponse> getChatList(
        @RequestParam(required = false, defaultValue = "0") Integer page,
        @RequestParam(required = false, defaultValue = "10") Integer size,
        @RequestParam(required = false, defaultValue = "ADMIN") ChannelType channelType
    ) {
        try {
            log.info("카카오톡 채팅 목록 조회 요청: page={}, size={}, channel={}", page, size, channelType);
            
            KakaoRequestDTO.ChatListRequest request = KakaoRequestDTO.ChatListRequest.builder()
                .page(page)
                .size(size)
                .build();
            
            KakaoResponseDTO.ChatListResponse response = kakaoService.getChatList(request, channelType);
            
            log.info("카카오톡 채팅 목록 조회 성공");
            return ApiResponse.onSuccess(SuccessStatus.KAKAO_OK, response);
            
        } catch (Exception e) {
            log.error("카카오톡 채팅 목록 조회 실패", e);
            throw e;
        }
    }

    @Operation(
        summary = "카카오톡 서비스 상태 확인",
        description = "카카오톡 웹훅 서비스의 상태를 확인합니다. channelType: ADMIN 또는 TEACHER"
    )
    @GetMapping("/health")
    public ApiResponse<Boolean> checkKakaoServiceHealth(
        @RequestParam(required = false, defaultValue = "ADMIN") ChannelType channelType
    ) {
        try {
            boolean isHealthy = kakaoService.isServiceHealthy(channelType);
            
            log.info("카카오톡 서비스 헬스체크: channel={}, isHealthy={}", channelType, isHealthy);
            return ApiResponse.onSuccess(SuccessStatus.KAKAO_OK, isHealthy);
            
        } catch (Exception e) {
            log.error("카카오톡 서비스 헬스체크 실패", e);
            throw e;
        }
    }
}
