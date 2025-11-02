package Capstone.CSmart.global.service.kakao;

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

    @Value("${kakao.webhook.url}")
    private String kakaoWebhookUrl;

    /**
     * 카카오톡 메시지 전송
     *
     * @param request 메시지 전송 요청
     * @return 메시지 전송 응답
     */
    public KakaoResponseDTO.SendMessageResponse sendMessage(KakaoRequestDTO.SendMessageRequest request) {
        try {
            String url = kakaoWebhookUrl + "/api/message/send";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<KakaoRequestDTO.SendMessageRequest> entity = new HttpEntity<>(request, headers);
            
            log.info("카카오톡 메시지 전송 요청: recipient={}, chatId={}", 
                request.getRecipient(), request.getChatId());
            
            ResponseEntity<KakaoResponseDTO.SendMessageResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                KakaoResponseDTO.SendMessageResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("카카오톡 메시지 전송 성공: messageId={}", 
                    response.getBody().getResult() != null ? response.getBody().getResult().getMessageId() : "N/A");
                return response.getBody();
            } else {
                log.error("카카오톡 메시지 전송 실패: status={}", response.getStatusCode());
                throw new RuntimeException("카카오톡 메시지 전송 실패");
            }
            
        } catch (RestClientException e) {
            log.error("카카오톡 메시지 전송 중 오류 발생", e);
            throw new RuntimeException("카카오톡 메시지 전송 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 카카오톡 채팅 목록 조회
     *
     * @param request 채팅 목록 조회 요청
     * @return 채팅 목록 응답
     */
    public KakaoResponseDTO.ChatListResponse getChatList(KakaoRequestDTO.ChatListRequest request) {
        try {
            String url = kakaoWebhookUrl + "/api/message/chat-list";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<KakaoRequestDTO.ChatListRequest> entity = new HttpEntity<>(request, headers);
            
            log.info("카카오톡 채팅 목록 조회 요청");
            
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
                
                log.info("카카오톡 채팅 목록 조회 성공: totalCount={}", totalCount);
                return response.getBody();
            } else {
                log.error("카카오톡 채팅 목록 조회 실패: status={}", response.getStatusCode());
                throw new RuntimeException("카카오톡 채팅 목록 조회 실패");
            }
            
        } catch (RestClientException e) {
            log.error("카카오톡 채팅 목록 조회 중 오류 발생", e);
            throw new RuntimeException("카카오톡 채팅 목록 조회 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 카카오톡 서비스 헬스체크
     *
     * @return 서비스 상태
     */
    public boolean isServiceHealthy() {
        try {
            String url = kakaoWebhookUrl + "/api/health";
            
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (RestClientException e) {
            log.error("카카오톡 서비스 헬스체크 실패", e);
            return false;
        }
    }
}
