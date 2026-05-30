FROM maven:3.8-openjdk-8 AS builder

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

COPY src ./src
RUN mvn clean package -DskipTests -B -q

FROM openjdk:8-jre-slim

RUN apt-get update && \
    apt-get install -y --no-install-recommends python3 python3-pip && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/target/ai-dev-hub-1.0.0-SNAPSHOT.jar app.jar

COPY orchestrator/ ./orchestrator/

RUN pip3 install --no-cache-dir -r orchestrator/requirements.txt

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
