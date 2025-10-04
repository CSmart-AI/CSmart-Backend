# 1. OpenJDK 17 기반 이미지 사용
FROM openjdk:17-jdk-slim

# 2. Maintainer 정보
LABEL maintainer="csmart-team@example.com"

# 3. 작업 디렉토리 설정
WORKDIR /app

# 4. JAR 파일을 컨테이너 내부로 복사
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

# 5. 포트 노출
EXPOSE 8080

# 6. 컨테이너 실행 시 Spring Boot 실행
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

