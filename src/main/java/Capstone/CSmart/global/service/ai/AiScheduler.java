package Capstone.CSmart.global.service.ai;

import Capstone.CSmart.global.domain.entity.Message;
import Capstone.CSmart.global.repository.AiResponseRepository;
import Capstone.CSmart.global.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiScheduler {

    private final MessageRepository messageRepository;
    private final AiResponseRepository aiResponseRepository;
    private final AiResponseService aiResponseService;
    private final RedisTemplate<String, String> redisTemplate;
    
    private static final String SCHEDULER_LOCK_KEY = "ai_scheduler_processing";
    private static final long SCHEDULER_LOCK_TTL_SECONDS = 600; // 10분 (스케줄러 실행 최대 시간)

    /**
     * 1분 30초마다 신규 메시지를 모아서 LangGraph 배치 처리
     * - 각 메시지를 개별적으로 처리
     * - 상담폼은 자동으로 건너뜀
     * - AI 응답이 없는 신규 메시지만 처리
     */
    @Scheduled(fixedDelay = 90000)  // 1분 30초 = 90,000ms
    public void processPendingMessages() {
        // Redis 분산 락으로 중복 실행 방지
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(
                SCHEDULER_LOCK_KEY, "processing", SCHEDULER_LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        
        if (Boolean.FALSE.equals(lockAcquired)) {
            log.warn("AI 스케줄러가 이미 실행 중입니다. 중복 실행 방지.");
            return;
        }

        try {
            OffsetDateTime since = OffsetDateTime.now().minusMinutes(30);

            log.info("AI 스케줄러 시작: 최근 30분간 신규 메시지 개별 처리");

        // AI 응답이 없는 메시지만 필터링 (가장 최근 것만 확인)
        List<Message> inboundMessages = messageRepository.findAll().stream()
                .filter(m -> "student".equals(m.getSenderType()))
                .filter(m -> m.getSentAt() != null && m.getSentAt().isAfter(since))
                .filter(m -> aiResponseRepository.findTopByMessageIdOrderByGeneratedAtDesc(m.getMessageId()).isEmpty())
                .sorted((m1, m2) -> m1.getSentAt().compareTo(m2.getSentAt())) // 오래된 순
                .toList();

        if (inboundMessages.isEmpty()) {
            log.info("처리할 신규 메시지가 없습니다.");
            return;
        }

        log.info("{}개의 미처리 메시지 발견", inboundMessages.size());

        int processedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        // ✅ 각 메시지를 개별적으로 처리
        for (Message message : inboundMessages) {
            try {
                log.info("메시지 처리 중: messageId={}, studentId={}",
                        message.getMessageId(), message.getStudentId());

                // AI 응답 생성 (상담폼은 AiResponseService에서 자동으로 건너뜀)
                aiResponseService.generateResponse(message.getMessageId());
                processedCount++;

            } catch (Exception e) {
                // 상담폼인 경우 에러가 발생하지만 정상 동작
                if (e.getMessage().contains("상담폼")) {
                    skippedCount++;
                    log.info("상담폼 메시지 건너뜀: messageId={}", message.getMessageId());
                } else {
                    failedCount++;
                    log.error("메시지 처리 실패: messageId={}, error={}",
                            message.getMessageId(), e.getMessage(), e);
                }
            }
        }

            log.info("AI 스케줄러 완료: 처리={}, 스킵(상담폼)={}, 실패={}",
                    processedCount, skippedCount, failedCount);
        } finally {
            // 처리 완료 후 락 해제
            redisTemplate.delete(SCHEDULER_LOCK_KEY);
            log.debug("AI 스케줄러 락 해제");
        }
    }
}


