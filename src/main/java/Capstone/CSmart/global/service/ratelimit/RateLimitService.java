package Capstone.CSmart.global.service.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 Rate Limiter 서비스
 * Sliding Window 알고리즘 사용
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;
    
    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";
    
    /**
     * Rate Limit 체크 및 적용
     * 
     * @param identifier 식별자 (userId, IP, API 경로 등)
     * @param limit 제한 횟수
     * @param windowSeconds 시간 윈도우 (초)
     * @return 허용 여부
     */
    public boolean isAllowed(String identifier, int limit, long windowSeconds) {
        String key = RATE_LIMIT_KEY_PREFIX + identifier;
        long now = Instant.now().getEpochSecond();
        long windowStart = now - windowSeconds;
        
        try {
            // Sliding Window: 현재 시간 기준으로 윈도우 범위의 요청만 카운트
            ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
            
            // 윈도우 범위 밖의 오래된 요청 제거
            zSetOps.removeRangeByScore(key, 0, windowStart);
            
            // 현재 윈도우 내 요청 수 확인
            Long count = zSetOps.count(key, windowStart, now);
            
            if (count != null && count >= limit) {
                log.warn("Rate limit exceeded: identifier={}, count={}, limit={}, window={}s", 
                        identifier, count, limit, windowSeconds);
                return false;
            }
            
            // 현재 요청을 ZSet에 추가 (점수는 타임스탬프)
            zSetOps.add(key, String.valueOf(now), now);
            
            // 키 만료 시간 설정 (윈도우 시간 + 여유 시간)
            redisTemplate.expire(key, windowSeconds + 60, TimeUnit.SECONDS);
            
            log.debug("Rate limit check: identifier={}, count={}, limit={}", 
                    identifier, (count != null ? count + 1 : 1), limit);
            
            return true;
            
        } catch (Exception e) {
            log.error("Rate limit check failed: identifier={}", identifier, e);
            // Redis 오류 시 기본적으로 허용 (fail-open)
            return true;
        }
    }
    
    /**
     * 남은 요청 횟수 조회
     */
    public long getRemainingRequests(String identifier, int limit, long windowSeconds) {
        String key = RATE_LIMIT_KEY_PREFIX + identifier;
        long now = Instant.now().getEpochSecond();
        long windowStart = now - windowSeconds;
        
        try {
            ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
            zSetOps.removeRangeByScore(key, 0, windowStart);
            
            Long count = zSetOps.count(key, windowStart, now);
            long remaining = limit - (count != null ? count : 0);
            
            return Math.max(0, remaining);
        } catch (Exception e) {
            log.error("Failed to get remaining requests: identifier={}", identifier, e);
            return limit; // 오류 시 전체 제한 반환
        }
    }
    
    /**
     * Rate Limit 리셋 (테스트용)
     */
    public void reset(String identifier) {
        String key = RATE_LIMIT_KEY_PREFIX + identifier;
        redisTemplate.delete(key);
        log.info("Rate limit reset: identifier={}", identifier);
    }
}




