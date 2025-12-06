package Capstone.CSmart.global.service.cache;

import Capstone.CSmart.global.domain.entity.AiResponse;
import Capstone.CSmart.global.domain.entity.SemanticCache;
import Capstone.CSmart.global.repository.AiResponseRepository;
import Capstone.CSmart.global.repository.SemanticCacheRepository;
import Capstone.CSmart.global.service.embedding.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticCacheService {

    private final SemanticCacheRepository cacheRepository;
    private final EmbeddingService embeddingService;
    private final RedisTemplate<String, String> redisTemplate;
    private final AiResponseRepository aiResponseRepository;

    @Value("${semantic-cache.similarity-threshold:0.85}")
    private double similarityThreshold;

    @Value("${semantic-cache.cache-ttl:604800}") // 7일
    private long cacheTtl;

    private static final String REDIS_KEY_PREFIX = "semantic_cache:";
    private static final String REDIS_STATS_KEY = "semantic_cache_stats";

    /**
     * 시멘틱 캐시에서 유사한 답변 검색
     * 성능 최적화: 고신뢰도 캐시만 조회 + 유사도 1회만 계산
     */
    public Optional<SemanticCache> findSimilarAnswer(String question) {
        try {
            log.debug("Searching semantic cache for question: {}", question.substring(0, Math.min(question.length(), 100)));

            // 1. 질문 정규화 (띄어쓰기 차이 제거)
            String normalizedQuestion = normalizeTextForKeywords(question);
            
            // 2. 질문을 임베딩으로 변환 (정규화된 텍스트 사용)
            List<Double> questionEmbedding = embeddingService.generateEmbedding(normalizedQuestion);

            // 2. 신뢰도 높은 캐시만 조회 (성능 최적화)
            // 신뢰도 0.7 이상의 캐시만 검색 대상으로 함
            List<SemanticCache> highQualityCaches = cacheRepository
                .findByConfidenceScoreGreaterThanEqualOrderByConfidenceScoreDescHitCountDesc(
                    0.7,
                    org.springframework.data.domain.PageRequest.of(0, 200) // 최대 200개만
                );

            if (highQualityCaches.isEmpty()) {
                log.debug("No high-quality cache entries found");
                return Optional.empty();
            }

            log.debug("Searching {} high-quality cache entries", highQualityCaches.size());

            // 3. 유사도 계산 결과를 Map에 저장 (1회만 계산)
            java.util.Map<SemanticCache, Double> similarityMap = new java.util.HashMap<>();

            // 현재 질문의 핵심 키워드 추출 (명사, 동사 등) - 정규화된 텍스트 사용
            java.util.Set<String> questionKeywords = extractKeywords(normalizedQuestion);
            // 주제 키워드 추출 (과목명, 전공명 등)
            java.util.Set<String> questionSubjectKeywords = extractSubjectKeywords(normalizedQuestion);
            // 질문 유형 키워드 추출 (일정, 문제, 방법 등)
            java.util.Set<String> questionTypeKeywords = extractQuestionTypeKeywords(normalizedQuestion);

            for (SemanticCache cache : highQualityCaches) {
                try {
                    List<Double> cacheEmbedding = embeddingService.jsonToVector(cache.getEmbeddingJson());
                    double similarity = embeddingService.cosineSimilarity(questionEmbedding, cacheEmbedding);

                    if (similarity >= similarityThreshold) {
                        // 키워드 기반 필터링: 핵심 키워드가 완전히 다르면 제외
                        // 캐시된 질문도 정규화하여 비교
                        String normalizedCacheQuestion = normalizeTextForKeywords(cache.getQuestion());
                        java.util.Set<String> cacheKeywords = extractKeywords(normalizedCacheQuestion);
                        java.util.Set<String> cacheSubjectKeywords = extractSubjectKeywords(normalizedCacheQuestion);
                        java.util.Set<String> cacheTypeKeywords = extractQuestionTypeKeywords(normalizedCacheQuestion);
                        
                        // 1. 주제 키워드가 다르면 무조건 제외 (예: 영어 vs 수학)
                        // 한쪽에만 주제 키워드가 있어도 필터링 (예: "영어" vs "모집인원")
                        if (!questionSubjectKeywords.isEmpty() || !cacheSubjectKeywords.isEmpty()) {
                            if (!questionSubjectKeywords.isEmpty() && !cacheSubjectKeywords.isEmpty()) {
                                // 양쪽 모두 주제 키워드가 있는 경우: 교집합이 있어야 함
                                java.util.Set<String> subjectIntersection = new java.util.HashSet<>(questionSubjectKeywords);
                                subjectIntersection.retainAll(cacheSubjectKeywords);
                                
                                if (subjectIntersection.isEmpty()) {
                                    log.debug("Cache ID: {}, Similarity: {:.4f}, but subject keywords don't match - SKIPPED (Q: {}, C: {})",
                                        cache.getCacheId(), similarity, questionSubjectKeywords, cacheSubjectKeywords);
                                    continue;
                                }
                            } else {
                                // 한쪽에만 주제 키워드가 있는 경우: 다른 주제로 판단하여 제외
                                log.debug("Cache ID: {}, Similarity: {:.4f}, but subject keywords mismatch (one side has subject, other doesn't) - SKIPPED (Q: {}, C: {})",
                                    cache.getCacheId(), similarity, questionSubjectKeywords, cacheSubjectKeywords);
                                continue;
                            }
                        }
                        
                        // 2. 질문 유형 키워드가 다르면 제외 (예: 일정 vs 문제, 모집인원 vs 외워야)
                        // 한쪽에만 질문 유형 키워드가 있어도 필터링
                        if (!questionTypeKeywords.isEmpty() || !cacheTypeKeywords.isEmpty()) {
                            if (!questionTypeKeywords.isEmpty() && !cacheTypeKeywords.isEmpty()) {
                                // 양쪽 모두 질문 유형 키워드가 있는 경우: 교집합이 있어야 함
                                java.util.Set<String> typeIntersection = new java.util.HashSet<>(questionTypeKeywords);
                                typeIntersection.retainAll(cacheTypeKeywords);
                                
                                if (typeIntersection.isEmpty()) {
                                    log.debug("Cache ID: {}, Similarity: {:.4f}, but question type keywords don't match - SKIPPED (Q: {}, C: {})",
                                        cache.getCacheId(), similarity, questionTypeKeywords, cacheTypeKeywords);
                                    continue;
                                }
                            } else {
                                // 한쪽에만 질문 유형 키워드가 있는 경우: 다른 유형으로 판단하여 제외
                                log.debug("Cache ID: {}, Similarity: {:.4f}, but question type keywords mismatch (one side has type, other doesn't) - SKIPPED (Q: {}, C: {})",
                                    cache.getCacheId(), similarity, questionTypeKeywords, cacheTypeKeywords);
                                continue;
                            }
                        }
                        
                        // 3. 일반 키워드 필터링 (더 엄격한 조건)
                        if (hasSignificantKeywordOverlap(questionKeywords, cacheKeywords)) {
                            similarityMap.put(cache, similarity);
                            log.debug("Cache ID: {}, Similarity: {:.4f}, All keywords match", cache.getCacheId(), similarity);
                        } else {
                            log.debug("Cache ID: {}, Similarity: {:.4f}, but keywords don't match - SKIPPED",
                                cache.getCacheId(), similarity);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to calculate similarity for cacheId: {}", cache.getCacheId(), e);
                }
            }

            if (similarityMap.isEmpty()) {
                log.debug("No cache entries above similarity threshold {}", similarityThreshold);
                return Optional.empty();
            }

            // 4. 가장 유사도가 높은 캐시 선택
            var bestMatch = similarityMap.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey);

            // 5. 캐시 히트 시 통계 업데이트
            if (bestMatch.isPresent()) {
                SemanticCache cache = bestMatch.get();
                double similarity = similarityMap.get(cache);

                // ✅ DB에서 최신 값을 다시 조회하여 캐시 수정 반영 (JPA 1차 캐시 이슈 방지)
                // 새로운 트랜잭션에서 조회하여 1차 캐시를 우회
                SemanticCache freshCache = getFreshCacheFromDatabase(cache.getCacheId())
                        .orElse(cache); // 조회 실패 시 기존 캐시 사용

                log.info("🎯 캐시 히트! Cache ID: {}, Similarity: {:.4f}, Hit Count: {}, Answer length: {}",
                    freshCache.getCacheId(), similarity, freshCache.getHitCount(), freshCache.getAnswer().length());
                log.info("📝 현재 질문: {}", question);
                log.info("💾 캐시된 질문: {}", freshCache.getQuestion());

                // 비동기로 히트 카운트 업데이트 (성능을 위해)
                updateCacheHitAsync(freshCache.getCacheId());

                // Redis에서 빠른 접근용 저장 (최신 답변 사용)
                String redisKey = REDIS_KEY_PREFIX + freshCache.getCacheId();
                redisTemplate.opsForValue().set(redisKey, freshCache.getAnswer(), cacheTtl, TimeUnit.SECONDS);

                return Optional.of(freshCache);
            }

            log.debug("No similar cache found above threshold {}", similarityThreshold);
            return Optional.empty();

        } catch (Exception e) {
            log.error("Failed to search semantic cache", e);
            return Optional.empty();
        }
    }

    /**
     * 새로운 답변을 캐시에 저장
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public SemanticCache saveToCache(String question, String answer, Long responseId, double confidenceScore) {
        try {
            log.info("Saving to semantic cache: question={}, responseId={}, confidenceScore={}",
                question.substring(0, Math.min(question.length(), 50)), responseId, confidenceScore);

            // 1. 이미 저장된 응답인지 확인
            Optional<SemanticCache> existing = cacheRepository.findByOriginalResponseId(responseId);
            if (existing.isPresent()) {
                log.warn("Cache already exists for responseId: {}", responseId);
                return existing.get();
            }

            // 2. 임베딩 생성
            List<Double> embedding = embeddingService.generateEmbedding(question);
            String embeddingJson = embeddingService.vectorToJson(embedding);

            // 3. 캐시 키 생성
            String cacheKey = generateCacheKey(question);

            // 4. 캐시 엔트티 생성
            SemanticCache cache = SemanticCache.builder()
                .question(question)
                .answer(answer)
                .embeddingJson(embeddingJson)
                .confidenceScore(confidenceScore)
                .hitCount(0)
                .lastHitAt(OffsetDateTime.now())
                .originalResponseId(responseId)
                .cacheKey(cacheKey)
                .embeddingModel(embeddingService.getEmbeddingModel())
                .build();

            // 5. DB에 저장
            SemanticCache savedCache = cacheRepository.save(cache);

            // 6. Redis에도 저장 (빠른 접근용)
            String redisKey = REDIS_KEY_PREFIX + savedCache.getCacheId();
            redisTemplate.opsForValue().set(redisKey, answer, cacheTtl, TimeUnit.SECONDS);

            // 7. 통계 업데이트
            updateCacheStatsAsync();

            log.info("✅ Saved to semantic cache: cacheId={}", savedCache.getCacheId());
            return savedCache;

        } catch (Exception e) {
            log.error("Failed to save to semantic cache", e);
            throw new RuntimeException("Failed to save to semantic cache", e);
        }
    }

    /**
     * 캐시 답변 수정
     * REQUIRES_NEW를 사용하여 새로운 트랜잭션에서 처리하고, 1차 캐시 문제를 방지
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public SemanticCache updateCacheAnswer(Long cacheId, String newAnswer) {
        try {
            // 새로운 트랜잭션에서 조회하여 1차 캐시 이슈 방지
            SemanticCache cache = cacheRepository.findById(cacheId)
                    .orElseThrow(() -> new RuntimeException("Cache not found: " + cacheId));

            log.info("Updating cache answer: cacheId={}, oldAnswer length={}, newAnswer length={}",
                    cacheId, cache.getAnswer().length(), newAnswer.length());

            // 답변 업데이트
            cache.setAnswer(newAnswer);

            // DB에 저장 (트랜잭션 커밋 보장)
            SemanticCache updatedCache = cacheRepository.save(cache);
            
            // 트랜잭션 커밋을 보장하기 위해 flush
            cacheRepository.flush();

            // Redis에도 업데이트 (트랜잭션 커밋 후)
            String redisKey = REDIS_KEY_PREFIX + cacheId;
            redisTemplate.opsForValue().set(redisKey, newAnswer, cacheTtl, TimeUnit.SECONDS);

            // ✅ 관련된 모든 AiResponse 업데이트
            // 같은 캐시 답변을 사용하는 모든 PENDING_REVIEW 상태의 AiResponse를 찾아서 업데이트
            try {
                if (updatedCache.getOriginalResponseId() != null) {
                    Optional<AiResponse> originalResponseOpt = aiResponseRepository.findById(updatedCache.getOriginalResponseId());
                    if (originalResponseOpt.isPresent()) {
                        AiResponse originalResponse = originalResponseOpt.get();
                        Long messageId = originalResponse.getMessageId();
                        
                        // 원본 AiResponse 업데이트
                        if (originalResponse.getStatus().name().equals("PENDING_REVIEW")) {
                            originalResponse.setRecommendedResponse(newAnswer);
                            aiResponseRepository.save(originalResponse);
                            log.info("✅ 원본 AiResponse 업데이트: responseId={}, messageId={}, cacheId={}", 
                                    originalResponse.getResponseId(), messageId, cacheId);
                        }
                        
                        // 같은 messageId를 가진 모든 PENDING_REVIEW 상태의 AiResponse 찾아서 업데이트
                        List<AiResponse> pendingResponses = aiResponseRepository.findAllByMessageIdAndStatus(
                                messageId, 
                                Capstone.CSmart.global.domain.enums.AiResponseStatus.PENDING_REVIEW
                        );
                        
                        int updatedCount = 0;
                        for (AiResponse response : pendingResponses) {
                            // 원본은 이미 업데이트했으므로 제외
                            if (!response.getResponseId().equals(updatedCache.getOriginalResponseId())) {
                                response.setRecommendedResponse(newAnswer);
                                aiResponseRepository.save(response);
                                updatedCount++;
                                log.info("✅ 관련 AiResponse 업데이트: responseId={}, messageId={}, cacheId={}", 
                                        response.getResponseId(), messageId, cacheId);
                            }
                        }
                        
                        if (updatedCount > 0) {
                            log.info("✅ 총 {}개의 관련 AiResponse 업데이트 완료: messageId={}, cacheId={}", 
                                    updatedCount, messageId, cacheId);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("AiResponse 업데이트 실패 (캐시는 정상 업데이트됨): cacheId={}, error={}", 
                        cacheId, e.getMessage(), e);
            }

            log.info("✅ Cache answer updated: cacheId={}, DB와 Redis 모두 업데이트 완료", cacheId);
            return updatedCache;

        } catch (Exception e) {
            log.error("Failed to update cache answer: cacheId={}", cacheId, e);
            throw new RuntimeException("Failed to update cache answer: " + e.getMessage(), e);
        }
    }

    /**
     * DB에서 최신 캐시 값을 조회 (새로운 트랜잭션에서)
     * JPA 1차 캐시 문제를 방지하기 위해 REQUIRES_NEW 사용
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<SemanticCache> getFreshCacheFromDatabase(Long cacheId) {
        try {
            return cacheRepository.findById(cacheId);
        } catch (Exception e) {
            log.warn("Failed to get fresh cache from database: cacheId={}", cacheId, e);
            return Optional.empty();
        }
    }

    /**
     * 캐시 키 생성 (해시 기반)
     */
    private String generateCacheKey(String question) {
        return "q_" + Math.abs(question.hashCode()) + "_" + System.currentTimeMillis();
    }

    /**
     * 질문에서 핵심 키워드 추출
     * 한국어의 경우 주요 명사, 동사 등을 추출
     * 주제 키워드(과목명 등)를 별도로 추출
     * 띄어쓰기 정규화를 통해 "편입전형"과 "편입 전형"을 동일하게 처리
     */
    private java.util.Set<String> extractKeywords(String text) {
        java.util.Set<String> keywords = new java.util.HashSet<>();

        // 띄어쓰기 정규화: 연속 공백 제거 및 복합어 처리
        String normalizedText = normalizeTextForKeywords(text);
        
        // 공백으로 분리하고, 2글자 이상인 단어만 키워드로 추출
        String[] words = normalizedText.split("[\\s\\p{Punct}]+");
        for (String word : words) {
            word = word.trim().toLowerCase();
            // 2글자 이상이고, 불용어가 아닌 경우만 추가
            if (word.length() >= 2 && !isStopWord(word)) {
                keywords.add(word);
            }
        }

        return keywords;
    }
    
    /**
     * 키워드 추출을 위한 텍스트 정규화
     * 띄어쓰기 차이를 줄여서 "편입전형"과 "편입 전형"을 유사하게 처리
     */
    private String normalizeTextForKeywords(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // 연속 공백 제거
        text = text.replaceAll("\\s+", " ");
        
        // 한국어 복합어 패턴: 띄어쓰기 제거 (예: "편입 전형" -> "편입전형")
        // 하지만 너무 긴 단어는 분리 (예: "중앙대학교 편입" -> "중앙대학교 편입" 유지)
        // 주요 복합어 패턴 정규화
        text = text.replaceAll("편입\\s+전형", "편입전형")
                   .replaceAll("편입\\s+시험", "편입시험")
                   .replaceAll("편입\\s+일정", "편입일정")
                   .replaceAll("시험\\s+일정", "시험일정")
                   .replaceAll("시험\\s+전형", "시험전형")
                   .replaceAll("모의\\s+고사", "모의고사")
                   .replaceAll("단어\\s+장", "단어장")
                   .replaceAll("문제\\s+집", "문제집")
                   .replaceAll("오답\\s+노트", "오답노트")
                   .replaceAll("학습\\s+법", "학습법")
                   .replaceAll("커리\\s+큘럼", "커리큘럼");
        
        return text.trim();
    }
    
    /**
     * 주제 키워드 추출 (과목명, 전공명 등)
     * 예: 영어, 수학, 물리, 화학, 컴퓨터공학, 소프트웨어 등
     */
    private java.util.Set<String> extractSubjectKeywords(String text) {
        java.util.Set<String> subjectKeywords = new java.util.HashSet<>();
        
        // 주요 과목명 및 전공명 패턴
        java.util.Set<String> subjectPatterns = java.util.Set.of(
            "영어", "수학", "물리", "화학", "생물", "지구과학",
            "국어", "한국어", "문학",
            "역사", "지리", "사회",
            "컴퓨터", "소프트웨어", "프로그래밍", "코딩",
            "공학", "전기", "전자", "기계", "건축",
            "경제", "경영", "회계", "마케팅",
            "의학", "간호", "약학",
            "교육", "심리", "사회복지"
        );
        
        String lowerText = text.toLowerCase();
        for (String pattern : subjectPatterns) {
            if (lowerText.contains(pattern)) {
                subjectKeywords.add(pattern);
            }
        }
        
        return subjectKeywords;
    }
    
    /**
     * 질문 유형 키워드 추출 (질문의 목적/의도)
     * CSV 파일 분석 기반으로 확장된 질문 유형 패턴
     */
    private java.util.Set<String> extractQuestionTypeKeywords(String text) {
        java.util.Set<String> typeKeywords = new java.util.HashSet<>();
        
        // 텍스트 정규화 (띄어쓰기 차이 제거)
        String normalizedText = normalizeTextForKeywords(text);
        
        // 질문 유형 패턴 (CSV 분석 기반)
        java.util.Set<String> typePatterns = java.util.Set.of(
            // 일정/시기 관련
            "일정", "시기", "언제", "기간", "날짜", "전날", "직전", "시작", "끝", "마무리",
            "몇월", "몇일", "언제부터", "언제까지", "시작하는", "끝내는",
            
            // 방법/방식 관련
            "방법", "어떻게", "순서", "배분", "루틴", "복습", "계획", "공부", "학습",
            "외워야", "암기", "회독", "정리", "작성", "활용", "진행", "접근",
            
            // 문제/유형 관련
            "문제", "문제유형", "문제형식", "출제", "기출", "유형", "형식", "패턴",
            "어떤문제", "문제가", "문제를", "문제풀이",
            
            // 준비/대비 관련
            "준비", "준비방법", "전략", "대비", "점검", "확인",
            "준비해야", "대비해야", "준비하는",
            
            // 합격/경쟁 관련
            "합격률", "경쟁률", "난이도", "백분위", "성적", "점수", "등급",
            "몇점", "몇퍼센트", "상위",
            
            // 필요/요구 관련
            "필요", "필수", "요구사항", "중요", "필요한", "필요한가",
            "꼭", "반드시", "해야", "해야하나",
            
            // 선택/구매 관련
            "어떤", "어느", "선택", "구매", "교재", "단어장", "문제집",
            "어떤것", "어느것", "어떤걸", "어느걸",
            
            // 시간/양 관련
            "몇시간", "몇강", "몇개", "얼마나", "하루", "주말", "평일",
            "시간", "분량", "양", "비율", "비중",
            
            // 인원/모집 관련
            "모집인원", "인원", "명", "몇명", "정원", "모집", "선발",
            "지원자", "합격자",
            
            // 커리큘럼/진도 관련
            "진도", "커리큘럼", "과정", "단계", "레벨",
            "진도를", "진도가", "커리큘럼을",
            
            // 성적/실력 관련
            "실력", "올리려면", "올리는", "향상", "부족", "어렵", "느린", "빠른"
        );
        
        String lowerText = normalizedText.toLowerCase();
        for (String pattern : typePatterns) {
            if (lowerText.contains(pattern)) {
                typeKeywords.add(pattern);
            }
        }
        
        return typeKeywords;
    }

    /**
     * 불용어 체크 (한국어) 일단 단순하게만 처리
     */
    private boolean isStopWord(String word) {
        // 간단한 불용어 리스트
        java.util.Set<String> stopWords = java.util.Set.of(
            "은", "는", "이", "가", "을", "를", "의", "에", "에서", "로", "으로",
            "와", "과", "도", "만", "부터", "까지", "에게", "한테", "께",
            "해주세요", "해주", "주세요", "주", "해", "하", "할", "하는", "한",
            "때", "때문", "것", "거", "게", "건", "거야", "거예요",
            "어떤", "어떻게", "무엇", "뭐", "왜", "어디", "언제", "누구",
            "있", "없", "되", "안", "못"
        );
        return stopWords.contains(word);
    }

    /**
     * 두 키워드 집합 간의 유의미한 겹침이 있는지 확인
     * 더 엄격한 조건: 키워드 매칭 비율을 확인하여 단순히 "중앙대학교"만 겹치는 경우 제외
     */
    private boolean hasSignificantKeywordOverlap(java.util.Set<String> keywords1, java.util.Set<String> keywords2) {
        if (keywords1.isEmpty() || keywords2.isEmpty()) {
            return true; // 키워드가 없으면 필터링하지 않음
        }

        // 교집합 계산
        java.util.Set<String> intersection = new java.util.HashSet<>(keywords1);
        intersection.retainAll(keywords2);

        // 키워드가 너무 적으면 (2개 이하) 필터링하지 않음
        if (keywords1.size() <= 2 || keywords2.size() <= 2) {
            return !intersection.isEmpty();
        }

        // 교집합 비율 계산 (더 엄격한 조건)
        double overlapRatio = (double) intersection.size() / Math.min(keywords1.size(), keywords2.size());
        
        // 최소 30% 이상 겹쳐야 매칭 (단순히 하나만 겹치는 것으로는 부족)
        // 또는 핵심 키워드가 2개 이상 겹쳐야 함
        boolean hasEnoughOverlap = overlapRatio >= 0.3 || intersection.size() >= 2;
        
        if (!hasEnoughOverlap) {
            log.debug("Keyword overlap insufficient: ratio={:.2f}, intersection={}, keywords1={}, keywords2={}",
                overlapRatio, intersection.size(), keywords1.size(), keywords2.size());
        }
        
        return hasEnoughOverlap;
    }

    /**
     * 비동기로 캐시 히트 카운트 업데이트
     * public으로 변경 (Spring AOP 프록시를 위해 필요)
     */
    @org.springframework.scheduling.annotation.Async("cacheTaskExecutor")
    @org.springframework.transaction.annotation.Transactional
    public void updateCacheHitAsync(Long cacheId) {
        try {
            Optional<SemanticCache> cacheOpt = cacheRepository.findById(cacheId);
            if (cacheOpt.isPresent()) {
                SemanticCache cache = cacheOpt.get();
                cache.incrementHitCount();
                cacheRepository.save(cache);
                log.debug("비동기 캐시 히트 카운트 업데이트 완료: cacheId={}", cacheId);
            }
        } catch (Exception e) {
            log.error("Failed to update cache hit count for cacheId: {}", cacheId, e);
        }
    }

    /**
     * 비동기로 전체 캐시 통계 업데이트
     */
    @org.springframework.scheduling.annotation.Async("cacheTaskExecutor")
    public void updateCacheStatsAsync() {
        try {
            long totalCaches = cacheRepository.count();
            redisTemplate.opsForHash().put(REDIS_STATS_KEY, "total_caches", String.valueOf(totalCaches));
            redisTemplate.opsForHash().put(REDIS_STATS_KEY, "last_updated", String.valueOf(System.currentTimeMillis()));
            log.debug("비동기 캐시 통계 업데이트 완료: totalCaches={}", totalCaches);
        } catch (Exception e) {
            log.error("Failed to update cache stats", e);
        }
    }

    /**
     * 캐시 통계 조회
     */
    public CacheStatistics getCacheStatistics() {
        try {
            Object[] stats = cacheRepository.getCacheStatistics();

            long totalCount = stats != null && stats.length > 0 ? ((Number) stats[0]).longValue() : 0;
            long totalHits = stats != null && stats.length > 1 && stats[1] != null ? ((Number) stats[1]).longValue() : 0;
            double avgConfidence = stats != null && stats.length > 2 && stats[2] != null ? ((Number) stats[2]).doubleValue() : 0.0;

            double hitRate = totalCount > 0 ? (double) totalHits / (totalHits + totalCount) * 100 : 0.0;

            // 예상 비용 절감 계산 (캐시 히트당 $0.02 절약)
            double estimatedSavings = totalHits * 0.02;

            return CacheStatistics.builder()
                .totalCacheCount(totalCount)
                .totalHitCount(totalHits)
                .hitRate(hitRate)
                .averageConfidenceScore(avgConfidence)
                .estimatedCostSavings(estimatedSavings)
                .similarityThreshold(similarityThreshold)
                .build();

        } catch (Exception e) {
            log.error("Failed to get cache statistics", e);
            return CacheStatistics.builder()
                .totalCacheCount(0)
                .totalHitCount(0)
                .hitRate(0.0)
                .averageConfidenceScore(0.0)
                .estimatedCostSavings(0.0)
                .similarityThreshold(similarityThreshold)
                .build();
        }
    }

    /**
     * 신뢰도 높은 캐시 조회
     */
    public List<SemanticCache> getHighConfidenceCaches(double minConfidence, int limit) {
        return cacheRepository.findByConfidenceScoreGreaterThanEqualOrderByConfidenceScoreDescHitCountDesc(
            minConfidence,
            org.springframework.data.domain.PageRequest.of(0, limit)
        );
    }

    /**
     * 캐시 삭제 (낮은 신뢰도 또는 오래된 캐시 정리)
     */
    @Transactional
    public void cleanupLowQualityCaches(double minConfidence, int maxAge) {
        try {
            java.time.LocalDateTime cutoffDate = java.time.LocalDateTime.now().minusDays(maxAge);

            // 낮은 신뢰도 또는 오래된 캐시 조회
            List<SemanticCache> cachesToDelete = cacheRepository.findAll().stream()
                .filter(cache ->
                    cache.getConfidenceScore() < minConfidence ||
                    cache.getCreatedAt().isBefore(cutoffDate)
                )
                .toList();

            for (SemanticCache cache : cachesToDelete) {
                // Redis에서도 삭제
                String redisKey = REDIS_KEY_PREFIX + cache.getCacheId();
                redisTemplate.delete(redisKey);
            }

            // DB에서 삭제
            cacheRepository.deleteAll(cachesToDelete);

            log.info("Cleaned up {} low quality cache entries", cachesToDelete.size());

        } catch (Exception e) {
            log.error("Failed to cleanup low quality caches", e);
        }
    }

    /**
     * Repository 접근 (CacheWarmupService용)
     */
    public SemanticCacheRepository getCacheRepository() {
        return cacheRepository;
    }

    /**
     * 캐시 통계 DTO
     */
    public static record CacheStatistics(
        long totalCacheCount,
        long totalHitCount,
        double hitRate,
        double averageConfidenceScore,
        double estimatedCostSavings,
        double similarityThreshold
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private long totalCacheCount;
            private long totalHitCount;
            private double hitRate;
            private double averageConfidenceScore;
            private double estimatedCostSavings;
            private double similarityThreshold;

            public Builder totalCacheCount(long totalCacheCount) {
                this.totalCacheCount = totalCacheCount;
                return this;
            }

            public Builder totalHitCount(long totalHitCount) {
                this.totalHitCount = totalHitCount;
                return this;
            }

            public Builder hitRate(double hitRate) {
                this.hitRate = hitRate;
                return this;
            }

            public Builder averageConfidenceScore(double averageConfidenceScore) {
                this.averageConfidenceScore = averageConfidenceScore;
                return this;
            }

            public Builder estimatedCostSavings(double estimatedCostSavings) {
                this.estimatedCostSavings = estimatedCostSavings;
                return this;
            }

            public Builder similarityThreshold(double similarityThreshold) {
                this.similarityThreshold = similarityThreshold;
                return this;
            }

            public CacheStatistics build() {
                return new CacheStatistics(
                    totalCacheCount,
                    totalHitCount,
                    hitRate,
                    averageConfidenceScore,
                    estimatedCostSavings,
                    similarityThreshold
                );
            }
        }
    }
}
