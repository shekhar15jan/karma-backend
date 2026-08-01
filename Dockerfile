FROM maven:3.9-amazoncorretto-21-al2023 AS builder

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

FROM amazoncorretto:21-alpine AS runner

RUN apk add --no-cache curl python3 py3-pip
RUN pip3 install --break-system-packages edge-tts || pip3 install edge-tts

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=30s \
    CMD curl -f http://localhost:8000/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
