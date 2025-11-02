package Capstone.CSmart.global.web.dto.Kakao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class KakaoRequestDTO {

    /**
     * 카카오톡 메시지 전송 요청 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendMessageRequest {
        
        @NotBlank(message = "수신자는 필수입니다.")
        private String recipient;
        
        @NotBlank(message = "메시지 내용은 필수입니다.")
        private String message;
        
        @Builder.Default
        private String messageType = "text";
        
        private String imageUrl;
        
        private String fileName;
        
        @NotBlank(message = "채팅방 ID는 필수입니다.")
        private String chatId;
    }

    /**
     * 카카오톡 채팅 목록 조회 요청 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatListRequest {
        
        // 현재는 추가 파라미터 없음 (향후 확장 가능)
        // 예: 페이지네이션, 필터링 등
        private Integer page;
        private Integer size;
    }
}
