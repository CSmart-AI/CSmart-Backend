package Capstone.CSmart.global.web.dto.Kakao;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class KakaoResponseDTO {

    /**
     * 카카오톡 메시지 전송 응답 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendMessageResponse {
        
        private boolean success;
        
        private String message;
        
        private MessageResult result;
        
        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class MessageResult {
            private String messageId;
            private String sentAt;
            private String status;
        }
    }

    /**
     * 카카오톡 채팅 목록 응답 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatListResponse {
        
        private boolean success;
        
        private String message;
        
        private ChatListData data;
        
        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ChatListData {
            private String id;
            private String savedAt;
            private Integer totalCount;
            private ChatList chatList;
        }
        
        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ChatList {
            private List<ChatItem> items;
            private boolean hasNext;
        }
        
        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ChatItem {
            private TalkUser talkUser;
            private String lastSeenLogId;
            private Long createdAt;
            private String lastMessage;
            private boolean isReplied;
            private boolean isRead;
            private Integer unreadCount;
            private boolean needManagerConfirm;
            private boolean isDeleted;
            private Long updatedAt;
            private String id;
            private Integer assigneeId;
            private String lastLogId;
            private boolean isDone;
            private String userLastSeenLogId;
            private Long version;
            private Long lastLogSendAt;
            private boolean isBlocked;
            private boolean isStarred;
            private boolean isUserLeft;
            private String profileId;
            private String encodedProfileId;
            private boolean aiFlag;
            private String name;
            private List<String> chatLabelIds;
            private boolean isFriend;
        }
        
        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TalkUser {
            private String statusMessage;
            private boolean active;
            private String profileImageUrl;
            private String chatId;
            private Integer userType;
            private String nickname;
            private String originalProfileImageUrl;
            private String id;
            private String fullProfileImageUrl;
        }
    }
}
