package Capstone.CSmart.global.service.kakao;

import Capstone.CSmart.global.domain.enums.ChannelType;
import Capstone.CSmart.global.domain.enums.UserRole;
import Capstone.CSmart.global.web.dto.Kakao.KakaoRequestDTO;
import Capstone.CSmart.global.web.dto.Kakao.KakaoResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoService {

    private final RestTemplate restTemplate;

    @Value("${kakao.webhook.url.admin}")
    private String adminWebhookUrl;
    
    @Value("${kakao.webhook.url.teacher}")
    private String teacherWebhookUrl;

    /**
     * 카카오톡 메시지 전송 (채널별)
     *
     * @param request 메시지 전송 요청
     * @param channelType 채널 타입 (ADMIN 또는 TEACHER)
     * @return 메시지 전송 응답
     */
    public KakaoResponseDTO.SendMessageResponse sendMessage(
            KakaoRequestDTO.SendMessageRequest request, 
            ChannelType channelType) {
        try {
            String webhookUrl = getWebhookUrlByChannelType(channelType);
            String url = webhookUrl + "/api/message/send";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<KakaoRequestDTO.SendMessageRequest> entity = new HttpEntity<>(request, headers);
            
            log.info("카카오톡 메시지 전송 요청: channel={}, recipient={}, chatId={}", 
                channelType, request.getRecipient(), request.getChatId());
            
            ResponseEntity<KakaoResponseDTO.SendMessageResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                KakaoResponseDTO.SendMessageResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("카카오톡 메시지 전송 성공: channel={}, messageId={}", 
                    channelType,
                    response.getBody().getResult() != null ? response.getBody().getResult().getMessageId() : "N/A");
                return response.getBody();
            } else {
                log.error("카카오톡 메시지 전송 실패: channel={}, status={}", channelType, response.getStatusCode());
                throw new RuntimeException("카카오톡 메시지 전송 실패");
            }
            
        } catch (RestClientException e) {
            log.error("카카오톡 메시지 전송 중 오류 발생: channel={}", channelType, e);
            throw new RuntimeException("카카오톡 메시지 전송 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 카카오톡 채팅 목록 조회 (채널별)
     *
     * @param request 채팅 목록 조회 요청
     * @param channelType 채널 타입 (ADMIN 또는 TEACHER)
     * @return 채팅 목록 응답
     */
    public KakaoResponseDTO.ChatListResponse getChatList(
            KakaoRequestDTO.ChatListRequest request, 
            ChannelType channelType) {
        try {
            String webhookUrl = getWebhookUrlByChannelType(channelType);
            String url = webhookUrl + "/api/message/chat-list";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<KakaoRequestDTO.ChatListRequest> entity = new HttpEntity<>(request, headers);
            
            log.info("카카오톡 채팅 목록 조회 요청: channel={}", channelType);
            
            ResponseEntity<KakaoResponseDTO.ChatListResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                KakaoResponseDTO.ChatListResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                int totalCount = response.getBody().getData() != null && 
                    response.getBody().getData().getChatList() != null && 
                    response.getBody().getData().getChatList().getItems() != null ?
                    response.getBody().getData().getChatList().getItems().size() : 0;
                
                log.info("카카오톡 채팅 목록 조회 성공: channel={}, totalCount={}", channelType, totalCount);
                return response.getBody();
            } else {
                log.error("카카오톡 채팅 목록 조회 실패: channel={}, status={}", channelType, response.getStatusCode());
                throw new RuntimeException("카카오톡 채팅 목록 조회 실패");
            }
            
        } catch (RestClientException e) {
            log.error("카카오톡 채팅 목록 조회 중 오류 발생: channel={}", channelType, e);
            throw new RuntimeException("카카오톡 채팅 목록 조회 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 카카오톡 서비스 헬스체크 (채널별)
     *
     * @param channelType 채널 타입 (ADMIN 또는 TEACHER)
     * @return 서비스 상태
     */
    public boolean isServiceHealthy(ChannelType channelType) {
        try {
            String webhookUrl = getWebhookUrlByChannelType(channelType);
            String url = webhookUrl + "/api/health";
            
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (RestClientException e) {
            log.error("카카오톡 서비스 헬스체크 실패: channel={}", channelType, e);
            return false;
        }
    }

    /**
     * 카카오 계정 로그인 검증 (웹훅 서버에 임시 로그인)
     *
     * @param kakaoId 카카오 계정 아이디
     * @param kakaoPassword 카카오 계정 비밀번호
     * @param role 사용자 역할 (ADMIN 또는 TEACHER)
     * @return 로그인 성공 여부
     */
    public boolean verifyKakaoLogin(String kakaoId, String kakaoPassword, UserRole role) {
        try {
            String webhookUrl = getWebhookUrlByRole(role);
            String url = webhookUrl + "/api/kakao/verify-login";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            java.util.Map<String, String> requestBody = new java.util.HashMap<>();
            requestBody.put("kakaoId", kakaoId);
            requestBody.put("kakaoPassword", kakaoPassword);
            
            HttpEntity<java.util.Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
            
            log.info("카카오 로그인 검증 요청: role={}, kakaoId={}", role, kakaoId);
            
            ResponseEntity<java.util.Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                java.util.Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Boolean success = (Boolean) response.getBody().get("success");
                log.info("카카오 로그인 검증 결과: role={}, success={}", role, success);
                return Boolean.TRUE.equals(success);
            } else {
                log.error("카카오 로그인 검증 실패: role={}, status={}", role, response.getStatusCode());
                return false;
            }
            
        } catch (RestClientException e) {
            log.error("카카오 로그인 검증 중 오류 발생: role={}", role, e);
            return false;
        }
    }

    /**
     * 카카오 웹훅 서버에 동적 로그인 요청
     *
     * @param kakaoId 카카오 계정 아이디
     * @param kakaoPassword 카카오 계정 비밀번호
     * @param role 사용자 역할 (ADMIN 또는 TEACHER)
     * @return 로그인 성공 여부
     */
    public boolean loginToKakaoWebhook(String kakaoId, String kakaoPassword, UserRole role) {
        try {
            String webhookUrl = getWebhookUrlByRole(role);
            String url = webhookUrl + "/api/kakao/login";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            java.util.Map<String, String> requestBody = new java.util.HashMap<>();
            requestBody.put("kakaoId", kakaoId);
            requestBody.put("kakaoPassword", kakaoPassword);
            
            HttpEntity<java.util.Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
            
            log.info("웹훅 서버 로그인 요청: role={}, url={}", role, webhookUrl);
            
            ResponseEntity<java.util.Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                java.util.Map.class
            );
            
            boolean success = response.getStatusCode().is2xxSuccessful();
            log.info("웹훅 서버 로그인 {}: role={}", success ? "성공" : "실패", role);
            
            return success;
        } catch (RestClientException e) {
            log.error("웹훅 서버 로그인 실패: role={}, error={}", role, e.getMessage());
            return false;
        }
    }

    /**
     * 카카오 웹훅 서버에서 로그아웃
     *
     * @param role 사용자 역할 (ADMIN 또는 TEACHER)
     */
    public void logoutFromKakaoWebhook(UserRole role) {
        try {
            String webhookUrl = getWebhookUrlByRole(role);
            String url = webhookUrl + "/api/kakao/logout";
            
            log.info("웹훅 서버 로그아웃 요청: role={}, url={}", role, webhookUrl);
            
            ResponseEntity<java.util.Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                null,
                java.util.Map.class
            );
            
            boolean success = response.getStatusCode().is2xxSuccessful();
            log.info("웹훅 서버 로그아웃 {}: role={}", success ? "성공" : "실패", role);
        } catch (RestClientException e) {
            log.error("웹훅 서버 로그아웃 실패: role={}, error={}", role, e.getMessage());
        }
    }

    /**
     * UserRole에 맞는 웹훅 서버 URL 반환
     *
     * @param role 사용자 역할
     * @return 웹훅 서버 URL
     */
    private String getWebhookUrlByRole(UserRole role) {
        return switch (role) {
            case ADMIN -> adminWebhookUrl;
            case TEACHER -> teacherWebhookUrl;
            default -> throw new IllegalArgumentException("Invalid role: " + role);
        };
    }

    /**
     * ChannelType에 맞는 웹훅 서버 URL 반환
     *
     * @param channelType 채널 타입
     * @return 웹훅 서버 URL
     */
    private String getWebhookUrlByChannelType(ChannelType channelType) {
        return switch (channelType) {
            case ADMIN -> adminWebhookUrl;
            case TEACHER -> teacherWebhookUrl;
        };
    }
}
