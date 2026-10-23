# บริการ AI จำแนกอาหาร (Flask + onnxruntime บน CPU ไม่ใช้ PyTorch จึงใช้ RAM ~200-300 MB ใส่แพ็กเกจฟรี 512 MB ได้)
# build จากโฟลเดอร์ yolo-service ต้องมี models/resnet50_thfood101.onnx และ models/classes_thfood101.json
# (สร้าง .onnx จาก .pt ด้วย tools/export_onnx.py)
FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1
WORKDIR /srv

RUN pip install flask pillow numpy onnxruntime gunicorn

COPY app.py .
COPY models/resnet50_thfood101.onnx models/classes_thfood101.json ./models/

RUN useradd --system --uid 10001 ai && chown -R ai /srv
USER ai
EXPOSE 5000
# โหลดโมเดลตอน import จึงใช้ worker เดียว (หลาย thread) ไม่ให้กินหน่วยความจำซ้ำ; Render ส่งพอร์ตมาทาง $PORT
CMD ["sh", "-c", "exec gunicorn --workers 1 --threads 2 --timeout 120 --bind 0.0.0.0:${PORT:-5000} app:app"]
