# Hugging Face Space (Docker, CPU Basic ฟรี: 2 vCPU / 16 GB RAM) — รันเว็บ Foodlen (Java) และบริการ AI (ONNX) ใน container เดียว
# ไฟล์นี้อยู่ใน Space (ไม่ใช่ใน repo โค้ด): ตอน build จะดาวน์โหลดโค้ดจาก GitHub branch "ปิยพันธ์" (repo ต้องเป็น public) แล้ว build ให้
# บริการ AI ฟังที่ 127.0.0.1:5000 ภายใน container เท่านั้น จึงไม่ต้องใช้รหัสลับและไม่เปิดสู่ภายนอก เว็บฟังพอร์ต 7860 (ตาม app_port ใน README.md)

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
ARG CODE_TARBALL=https://codeload.github.com/pattadonkh-bit/Foodlen/tar.gz/refs/heads/%E0%B8%9B%E0%B8%B4%E0%B8%A2%E0%B8%9E%E0%B8%B1%E0%B8%99%E0%B8%98%E0%B9%8C
ADD ${CODE_TARBALL} /tmp/foodlen.tar.gz
RUN tar xzf /tmp/foodlen.tar.gz --strip-components=1 \
    && cd demo \
    && mvn -q -B -Dmaven.test.skip=true package

FROM python:3.11-slim-bookworm
RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-17-jre-headless \
    && rm -rf /var/lib/apt/lists/* \
    && pip install --no-cache-dir flask pillow numpy onnxruntime gunicorn

# Hugging Face รัน container ด้วยผู้ใช้ uid 1000
RUN useradd -m -u 1000 user
USER user
WORKDIR /home/user/app

COPY --from=build --chown=user /src/demo/target/*.jar app.jar
COPY --from=build --chown=user /src/demo/yolo-service/app.py ai/app.py
COPY --from=build --chown=user /src/demo/yolo-service/models/resnet50_thfood101.onnx /src/demo/yolo-service/models/classes_thfood101.json ai/models/
COPY --chown=user start.sh start.sh

# ฐานข้อมูล (Neon), บัญชีครู ฯลฯ ตั้งเป็น Secrets ใน Settings ของ Space — ดู README ของโปรเจกต์ (หัวข้อ Hugging Face Spaces)
ENV SERVER_PORT=7860 \
    FOODLEN_AI_SERVICE_URL=http://127.0.0.1:5000 \
    FOODLEN_SEED_DEMO=false \
    SERVER_FORWARD_HEADERS_STRATEGY=framework \
    SERVER_SERVLET_SESSION_COOKIE_SECURE=true \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50 -XX:+UseSerialGC"

EXPOSE 7860
CMD ["sh", "start.sh"]
