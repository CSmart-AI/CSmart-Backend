package Capstone.CSmart.global.service.ai;

import Capstone.CSmart.global.domain.entity.Message;
import Capstone.CSmart.global.repository.AiResponseRepository;
import Capstone.CSmart.global.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiScheduler {

    private final MessageRepository messageRepository;
    private final AiResponseRepository aiResponseRepository;
    private final AiResponseService aiResponseService;

    /**
     * 10분마다 신규 메시지를 모아서 LangGraph 배치 처리
     * - 너무 많은 요청을 방지하기 위해 메시지를 모아서 처리
     * - AI 응답이 없는 신규 메시지만 처리
     */
    @Scheduled(fixedDelay = 600000)  // 10분 = 600,000ms
    public void processPendingMessages() {
        OffsetDateTime since = OffsetDateTime.now().minusMinutes(10);

        log.info("AI 스케줄러 시작: 최근 10분간 신규 메시지 배치 처리");

        List<Message> inboundMessages = messageRepository.findAll().stream()
                .filter(m -> "student".equals(m.getSenderType()))
                .filter(m -> m.getSentAt() != null && m.getSentAt().isAfter(since))
                .toList();

        if (inboundMessages.isEmpty()) {
            log.info("처리할 신규 메시지가 없습니다.");
            return;
        }

        log.info("{}개의 학생 메시지 발견", inboundMessages.size());

        int processedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (Message message : inboundMessages) {
            try {
                if (aiResponseRepository.findByMessageId(message.getMessageId()).isPresent()) {
                    skippedCount++;
                    continue;
                }

                log.debug("LangGraph 처리 중: messageId={}", message.getMessageId());
                aiResponseService.generateResponse(message.getMessageId());
                processedCount++;

            } catch (Exception e) {
                failedCount++;
                log.error("메시지 처리 실패: messageId={}, error={}", 
                        message.getMessageId(), e.getMessage());
            }
        }

        log.info("AI 스케줄러 완료: 처리={}, 스킵={}, 실패={}", 
                processedCount, skippedCount, failedCount);
    }
}


