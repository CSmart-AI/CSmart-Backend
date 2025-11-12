# 빌드 스테이지 추가함
FROM gradle:8-jdk17 AS build
WORKDIR /app

COPY build.gradle settings.gradle gradle.properties ./
COPY gradle ./gradle
COPY gradlew ./
RUN chmod +x gradlew

RUN ./gradlew dependencies --no-daemon || true

COPY src ./src

RUN ./gradlew build -x test --no-daemon

# 기존: 1. 실행 스테이지
# NOTE: 기존 openjdk 이미지 depreacted됨, 관련 링크: https://hub.docker.com/_/openjdk/?utm_source=chatgpt.com
FROM eclipse-temurin:17-jre-jammy

# 2. 작업 디렉토리 설정
WORKDIR /app

# 3. 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 4. 포트 노출
EXPOSE 8080

# 5. 컨테이너 실행 시 Spring Boot 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
