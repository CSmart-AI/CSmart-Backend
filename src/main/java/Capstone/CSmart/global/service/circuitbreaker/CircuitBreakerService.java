package Capstone.CSmart.global.service.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Circuit Breaker 서비스
 * 외부 API 호출에 대한 장애 격리 및 자동 복구
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CircuitBreakerService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Circuit Breaker로 보호된 실행
     * 
     * @param circuitBreakerName Circuit Breaker 인스턴스 이름
     * @param supplier 실행할 함수
     * @return 실행 결과
     */
    public <T> T execute(String circuitBreakerName, Supplier<T> supplier) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
        
        return CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            try {
                T result = supplier.get();
                log.debug("Circuit breaker execution success: name={}", circuitBreakerName);
                return result;
            } catch (Exception e) {
                log.error("Circuit breaker execution failed: name={}, error={}", 
                        circuitBreakerName, e.getMessage());
                throw e;
            }
        }).get();
    }

    /**
     * Circuit Breaker 상태 조회
     */
    public CircuitBreaker.State getState(String circuitBreakerName) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
        return circuitBreaker.getState();
    }

    /**
     * Circuit Breaker 메트릭 조회
     */
    public CircuitBreaker.Metrics getMetrics(String circuitBreakerName) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
        return circuitBreaker.getMetrics();
    }
}




