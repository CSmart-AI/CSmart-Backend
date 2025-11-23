package Capstone.CSmart.global.web.dto.Kakao;

import Capstone.CSmart.global.domain.enums.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 카카오톡 웹훅 메시지 DTO
 * 웹훅 서버로부터 수신한 메시지 데이터
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KakaoMessageDTO {
    
    /**
     * 카카오톡 사용자 ID (익명 해시)
     */
    private String userId;
    
    /**
     * 사용자 이름 (옵션)
     */
    private String userName;
    
    /**
     * 사용자 타입 (botUserKey 등)
     */
    private String userType;
    
    /**
     * 사용자가 입력한 메시지
     */
    private String utterance;
    
    /**
     * 메시지 수신 시간 (ISO 8601 형식)
     */
    private String timestamp;
    
    /**
     * 카카오톡 봇 ID (채널 구분용)
     */
    private String botId;
    
    /**
     * 카카오톡 봇 이름 (채널 이름)
     */
    private String botName;
    
    /**
     * 액션 이름 (카카오톡 스킬 액션)
     */
    private String actionName;
    
    /**
     * 채널 타입 (ADMIN 또는 TEACHER)
     * 웹훅 서버의 CHANNEL_TYPE 환경변수로부터 자동 설정됨
     */
    private ChannelType channelType;
}


