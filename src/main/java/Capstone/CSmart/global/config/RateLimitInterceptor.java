package Capstone.CSmart.global.config;

import Capstone.CSmart.global.service.ratelimit.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rate Limiting 인터셉터
 * API 엔드포인트별로 Rate Limit 적용
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    
    // 기본 Rate Limit 설정
    @Value("${rate-limit.default.limit:100}")
    private int defaultLimit;
    
    @Value("${rate-limit.default.window:60}")
    private long defaultWindowSeconds;
    
    // AI API Rate Limit (비용 절감)
    @Value("${rate-limit.ai.limit:20}")
    private int aiLimit;
    
    @Value("${rate-limit.ai.window:60}")
    private long aiWindowSeconds;
    
    // 웹훅 Rate Limit (DDoS 방지)
    @Value("${rate-limit.webhook.limit:50}")
    private int webhookLimit;
    
    @Value("${rate-limit.webhook.window:60}")
    private long webhookWindowSeconds;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        String identifier = getIdentifier(request);
        
        // Rate Limit 설정 결정
        int limit;
        long windowSeconds;
        
        if (path.contains("/api/ai/") || path.contains("/api/gemini/")) {
            // AI API: 비용 절감을 위해 엄격한 제한
            limit = aiLimit;
            windowSeconds = aiWindowSeconds;
        } else if (path.contains("/api/kakao/webhook/")) {
            // 웹훅: DDoS 방지
            limit = webhookLimit;
            windowSeconds = webhookWindowSeconds;
        } else {
            // 기본 제한
            limit = defaultLimit;
            windowSeconds = defaultWindowSeconds;
        }
        
        // Rate Limit 체크
        if (!rateLimitService.isAllowed(identifier, limit, windowSeconds)) {
            long remaining = rateLimitService.getRemainingRequests(identifier, limit, windowSeconds);
            
            response.setStatus(429); // SC_TOO_MANY_REQUESTS
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + windowSeconds));
            response.setContentType("application/json");
            
            try {
                response.getWriter().write(
                    String.format("{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again in %d seconds.\"}", 
                    windowSeconds)
                );
            } catch (Exception e) {
                log.error("Failed to write rate limit response", e);
            }
            
            log.warn("Rate limit exceeded: path={}, identifier={}, limit={}/{}s", 
                    path, identifier, limit, windowSeconds);
            
            return false;
        }
        
        // 남은 요청 수 헤더 추가
        long remaining = rateLimitService.getRemainingRequests(identifier, limit, windowSeconds);
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + windowSeconds));
        
        return true;
    }
    
    /**
     * 요청 식별자 추출 (IP 또는 UserId)
     */
    private String getIdentifier(HttpServletRequest request) {
        // JWT에서 userId 추출 시도
        String userId = (String) request.getAttribute("userId");
        if (userId != null) {
            return "user:" + userId;
        }
        
        // IP 주소 사용
        String ip = getClientIpAddress(request);
        return "ip:" + ip;
    }
    
    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}

