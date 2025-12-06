# CSmart Backend

편입 상담 챗봇 플랫폼 백엔드 서버

## 개요

CSmart Backend는 편입 상담을 위한 AI 챗봇 시스템의 백엔드 서버입니다. KakaoTalk 웹훅과 연동하여 학생의 편입 상담을 처리하고, Gemini API와 LangGraph를 활용한 AI 응답 생성 기능을 제공합니다.

## 기술 스택

- Java 17
- Spring Boot 3.4.5
- Spring Data JPA
- Spring Security
- MySQL 8.0
- Redis
- JWT
- Resilience4j (Circuit Breaker)
- Swagger/OpenAPI 3

## 주요 기능

### 학생 관리
- 학생 정보 CRUD
- 학생 정보 추출 (Gemini API 기반)
- 학생 Transfer (선생님 배정)
- 학생 완전 삭제

### 메시지 및 AI 응답 관리
- 메시지 수신 및 저장
- AI 응답 생성 (Gemini / LangGraph)
- AI 응답 검토 및 승인
- Transfer 전/후 구분하여 응답 생성 방식 선택

### Semantic Cache
- 유사 질문 캐시 히트
- 텍스트 정규화 및 키워드 추출
- 캐시 답변 수정 API
- 신뢰도 기반 캐시 관리

### 안정성 및 성능
- Circuit Breaker (Gemini API, LangGraph API)
- Rate Limiting (API별 차등 적용)
- Redis 기반 분산 락
- 스케줄러 기반 배치 처리

## 프로젝트 구조

```
src/main/java/Capstone/CSmart/global/
├── apiPayload/          # API 응답 공통 형식
├── config/              # 설정 클래스
│   ├── SecurityConfig.java
│   ├── RedisConfig.java
│   ├── SwaggerConfig.java
│   └── WebConfig.java
├── domain/              # 도메인 모델
│   ├── entity/          # 엔티티
│   └── enums/           # 열거형
├── repository/          # 데이터 접근 계층
├── security/            # 보안 관련
│   ├── filter/          # JWT 필터
│   └── provider/        # JWT 토큰 제공자
├── service/             # 비즈니스 로직
│   ├── ai/              # AI 응답 생성
│   ├── cache/           # Semantic Cache
│   ├── circuitbreaker/  # Circuit Breaker
│   ├── embedding/       # 텍스트 임베딩
│   ├── gemini/          # Gemini API 연동
│   ├── ratelimit/       # Rate Limiting
│   └── transfer/        # 학생 Transfer
└── web/                 # 웹 계층
    ├── controller/      # REST 컨트롤러
    └── dto/             # 데이터 전송 객체
```

## 환경 설정

### 필수 환경 변수

`.env` 파일 또는 환경 변수로 다음 값들을 설정해야 합니다:

```bash
# 데이터베이스
RDS_USERNAME=your_username
RDS_PASSWORD=your_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT
JWT_SECRET_KEY=your-secret-key-at-least-256-bits

# Gemini API
GEMINI_API_KEY=your_gemini_api_key

# LangGraph
LANGGRAPH_URL=http://csmart-langraph:8000

# KakaoTalk Webhook
KAKAO_WEBHOOK_URL_ADMIN=http://kakao-webhook-admin:3001
KAKAO_WEBHOOK_URL_TEACHER=http://kakao-webhook-teacher:3002
```

### application.yml 주요 설정

- 데이터베이스: MySQL 8.0
- Redis: Semantic Cache 및 분산 락용
- Circuit Breaker: Resilience4j 기반
- Rate Limiting: API별 차등 적용

## 빌드 및 실행

### 로컬 개발 환경

```bash
# Gradle 빌드
./gradlew build

# 애플리케이션 실행
./gradlew bootRun
```

### Docker Compose 실행

```bash
# 프로젝트 루트에서 실행
docker-compose up -d csmart-backend

# 로그 확인
docker-compose logs -f csmart-backend
```

## API 문서

서버 실행 후 Swagger UI에서 API 문서를 확인할 수 있습니다:

```
http://localhost:8080/swagger-ui/index.html
```

## 주요 API 엔드포인트

### 학생 관리
- `GET /api/students` - 학생 목록 조회
- `GET /api/students/{id}` - 학생 상세 조회
- `PUT /api/students/{id}` - 학생 정보 수정
- `DELETE /api/students/{id}/complete` - 학생 완전 삭제

### AI 응답 관리
- `GET /api/ai-responses/pending` - 검토 대기 응답 목록
- `GET /api/ai-responses/pending/teacher/{teacherId}` - 선생님별 검토 대기 응답
- `POST /api/ai-responses/{id}/approve` - 응답 승인
- `PUT /api/ai-responses/{id}/edit` - 응답 수정 및 승인

### Semantic Cache
- `GET /api/cache/semantic/stats` - 캐시 통계
- `PUT /api/cache/semantic/{cacheId}` - 캐시 답변 수정
- `POST /api/cache/semantic/warmup` - 캐시 워밍업

### Gemini API
- `POST /api/gemini/extract/{studentId}` - 학생 정보 추출
- `POST /api/gemini/summarize` - 첫 메시지 요약

## Circuit Breaker 설정

### Gemini API
- 슬라이딩 윈도우: 10회
- 실패율 임계값: 50%
- 느린 호출 임계값: 5초
- 열림 상태 대기 시간: 30초

### LangGraph API
- 슬라이딩 윈도우: 10회
- 실패율 임계값: 50%
- 느린 호출 임계값: 10초
- 열림 상태 대기 시간: 30초

## Rate Limiting 설정

- 기본: 1분에 100회
- AI API: 1분에 20회 (비용 절감)
- 웹훅: 1분에 50회 (DDoS 방지)

## Semantic Cache 설정

- 유사도 임계값: 0.92 (92% 이상)
- 캐시 TTL: 7일
- 임베딩 모델: text-embedding-004
- 임베딩 차원: 768

## 스케줄러

- AI 응답 생성 스케줄러: 1분 30초마다 실행
- 처리 대상: PENDING 상태의 메시지

## 보안

- JWT 기반 인증
- Spring Security 적용
- Role 기반 접근 제어 (ADMIN, TEACHER)
- API별 Rate Limiting

## 로깅

- SLF4J + Logback 사용
- 로그 레벨: application.yml에서 설정 가능
- 주요 작업 로그 기록 (학생 정보 추출, AI 응답 생성, 캐시 히트 등)

## 테스트

```bash
# 단위 테스트 실행
./gradlew test

# 통합 테스트 실행
./gradlew integrationTest
```

## 문제 해결

### Circuit Breaker OPEN 상태
- API 호출 실패율이 높을 때 발생
- application.yml에서 Circuit Breaker 설정 확인
- API 서비스 상태 확인 필요

### Redis 연결 실패
- Redis 서버 실행 상태 확인
- REDIS_HOST, REDIS_PORT 환경 변수 확인

### 데이터베이스 연결 실패
- MySQL 서버 실행 상태 확인
- RDS_USERNAME, RDS_PASSWORD 환경 변수 확인
- 데이터베이스 URL 확인

## 라이선스

프로젝트 내부 사용

## 연락처

프로젝트 관련 문의는 팀에 문의하세요.

