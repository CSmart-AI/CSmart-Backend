# 1. OpenJDK 17 기반 이미지 사용
FROM openjdk:17

# 3. JAR 파일을 컨테이너 내부로 복사
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

# 4. 컨테이너 실행 시 Spring Boot 실행
ENTRYPOINT ["java", "-jar", "/app.jar"]
