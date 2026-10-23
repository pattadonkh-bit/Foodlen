# เว็บแอป Foodlen (Spring Boot) — build: docker build -t foodlen-web .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre
RUN useradd --system --create-home --uid 10001 foodlen \
    && mkdir -p /app/data && chown -R foodlen /app
WORKDIR /app
COPY --from=build /build/target/*.jar /app/app.jar
USER foodlen
EXPOSE 8080
# ฐานข้อมูล H2 อยู่ที่ /app/data (ต้อง mount เป็น volume ไม่งั้นข้อมูลหายตอนสร้างคอนเทนเนอร์ใหม่)
ENV SPRING_DATASOURCE_URL=jdbc:h2:file:/app/data/foodlen \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
