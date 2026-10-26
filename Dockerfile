# 빌드 단계
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성만 먼저 받아 레이어로 캐시한다. src 가 바뀌어도 이 레이어는 재사용된다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

# 테스트는 CI에서 수행
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# 실행 단계
FROM eclipse-temurin:21-jre
WORKDIR /app

# docker-compose 의 postgres 와 동일
ENV TZ=Asia/Seoul

RUN useradd --system --create-home --shell /usr/sbin/nologin telme
USER telme

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
