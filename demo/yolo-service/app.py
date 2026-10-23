"""AI service: จำแนกอาหารไทยจากภาพด้วย ResNet50 ที่ fine-tune บน THFOOD-100 (+ข้าวเหนียวมะม่วง) รวม 101 เมนู

เรียกจาก Spring Boot (YoloService) ผ่าน HTTP:
    GET  /health
    POST /predict   (multipart: image)  ->  {"predictions":[{"code","confidence"},...], "model", "device"}

มี 2 backend (เลือกอัตโนมัติ):
    onnx   ถ้ามี models/resnet50_thfood101.onnx  -> onnxruntime (CPU) ไม่ต้องติดตั้ง PyTorch ใช้ RAM น้อย (เหมาะกับเซิร์ฟเวอร์ฟรี)
    torch  ถ้าไม่มีไฟล์ onnx                      -> PyTorch (ใช้ GPU ได้) สร้าง .onnx ด้วย tools/export_onnx.py

ตัวแปรสภาพแวดล้อม (ถ้าไม่กำหนดใช้ค่าเริ่มต้น):
    FOODLEN_BACKEND  auto | onnx | torch
    MODEL_PATH       models/resnet50_thfood101.onnx (หรือ .pt สำหรับ torch)
    CLASSES_PATH     models/classes_thfood101.json
    FOODLEN_DEVICE   auto | cpu | cuda   (torch เท่านั้น)
    FOODLEN_AI_TOKEN ถ้าตั้งไว้ /predict ต้องส่ง header X-Foodlen-Token ตรงกัน (ใช้เมื่อเปิดเป็น URL สาธารณะ)
    HOST / PORT      127.0.0.1 / 5000
"""
import hmac
import io
import json
import logging
import os
import re
from pathlib import Path

import numpy as np
from flask import Flask, jsonify, request
from PIL import Image, UnidentifiedImageError

BASE = Path(__file__).resolve().parent
ONNX_PATH = BASE / "models" / "resnet50_thfood101.onnx"
PT_PATH = BASE / "models" / "resnet50_thfood101.pt"
CLASSES_PATH = Path(os.environ.get("CLASSES_PATH", BASE / "models" / "classes_thfood101.json"))
TOP_K = 5
TOKEN = os.environ.get("FOODLEN_AI_TOKEN", "").strip()

MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32).reshape(1, 3, 1, 1)
STD = np.array([0.229, 0.224, 0.225], dtype=np.float32).reshape(1, 3, 1, 1)

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("foodlen-ai")

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 10 * 1024 * 1024


def choose_backend():
    wanted = os.environ.get("FOODLEN_BACKEND", "auto").lower()
    model_path = os.environ.get("MODEL_PATH")
    if wanted == "auto":
        if model_path:
            return "onnx" if model_path.endswith(".onnx") else "torch"
        return "onnx" if ONNX_PATH.exists() else "torch"
    return wanted


def load_onnx(path):
    import onnxruntime as ort
    opts = ort.SessionOptions()
    opts.intra_op_num_threads = int(os.environ.get("FOODLEN_THREADS", "2"))
    opts.enable_mem_pattern = False  # ลดการจองหน่วยความจำล่วงหน้า
    sess = ort.InferenceSession(str(path), sess_options=opts, providers=["CPUExecutionProvider"])
    name = sess.get_inputs()[0].name

    def logits(x):
        return sess.run(None, {name: x})[0][0]

    return logits, "cpu"


def load_torch(path):
    import torch
    import torch.nn as nn
    from torchvision import models

    n = len(json.loads(CLASSES_PATH.read_text(encoding="utf-8"))["classes"])
    model = models.resnet50(weights=None)
    model.fc = nn.Linear(model.fc.in_features, n)
    model.load_state_dict(torch.load(path, map_location="cpu", weights_only=True))
    wanted = os.environ.get("FOODLEN_DEVICE", "auto").lower()
    device = "cuda" if (wanted in ("auto", "cuda") and torch.cuda.is_available()) else "cpu"
    model.to(device).eval()

    def logits(x):
        with torch.no_grad():
            return model(torch.from_numpy(x).to(device)).float()[0].cpu().numpy()

    return logits, device


def load_model():
    meta = json.loads(CLASSES_PATH.read_text(encoding="utf-8"))
    backend = choose_backend()
    if backend == "onnx":
        fn, device = load_onnx(os.environ.get("MODEL_PATH", ONNX_PATH))
    else:
        fn, device = load_torch(os.environ.get("MODEL_PATH", PT_PATH))
    return fn, meta["classes"], device, backend, meta.get("model", "resnet50")


LOGITS, CLASSES, DEVICE, BACKEND, MODEL_NAME = load_model()
# รหัสเมนูที่ส่งกลับ = ชื่อโฟลเดอร์คลาสตัดเลขนำหน้า (เช่น 086_KhaoManKai -> KhaoManKai) ตรงกับ menu_nutrition.csv
CODES = [re.sub(r"^\d+_", "", c) for c in CLASSES]
log.info("loaded %s (%s) with %d classes on %s", MODEL_NAME, BACKEND, len(CLASSES), DEVICE)


def preprocess(img: Image.Image) -> np.ndarray:
    """เทียบเท่า torchvision: Resize(256) -> CenterCrop(224) -> ToTensor -> Normalize (ตรวจแล้วว่าผลตรงกัน)"""
    img = img.convert("RGB")
    w, h = img.size
    short, long_ = (w, h) if w <= h else (h, w)
    new_short, new_long = 256, int(256 * long_ / short)
    size = (new_short, new_long) if w <= h else (new_long, new_short)
    img = img.resize(size, Image.BILINEAR)
    w, h = img.size
    left, top = int(round((w - 224) / 2.0)), int(round((h - 224) / 2.0))
    img = img.crop((left, top, left + 224, top + 224))
    x = np.asarray(img, dtype=np.float32).transpose(2, 0, 1)[None] / 255.0
    return ((x - MEAN) / STD).astype(np.float32)


def predict_image(img: Image.Image, top_k: int = TOP_K):
    z = LOGITS(preprocess(img)).astype(np.float64)
    z -= z.max()
    p = np.exp(z)
    p /= p.sum()
    idx = np.argsort(-p)[: min(top_k, len(CLASSES))]
    return [{"code": CODES[i], "confidence": round(float(p[i]), 4)} for i in idx]


def authorized() -> bool:
    if not TOKEN:
        return True
    return hmac.compare_digest(request.headers.get("X-Foodlen-Token", ""), TOKEN)


@app.get("/health")
def health():
    # ไม่ต้องใช้ token (ให้ Render ตรวจสถานะได้) จึงตอบเฉพาะข้อมูลทั่วไป
    return jsonify({"status": "ok", "model": MODEL_NAME, "backend": BACKEND, "device": DEVICE})


@app.post("/predict")
def predict():
    if not authorized():
        return jsonify({"error": "unauthorized"}), 401
    f = request.files.get("image")
    if f is None or not f.filename:
        return jsonify({"error": "ไม่พบไฟล์ image"}), 400
    if not (f.mimetype or "").startswith("image/"):
        return jsonify({"error": "ไฟล์ต้องเป็นรูปภาพ"}), 400
    try:
        img = Image.open(io.BytesIO(f.read()))
        img.draft("RGB", (512, 512))  # JPEG ขนาดใหญ่: ถอดรหัสแบบย่อส่วน (ขนาดไม่ต่ำกว่า 512) ลดหน่วยความจำและเวลา
        img.load()
    except (UnidentifiedImageError, OSError):
        return jsonify({"error": "อ่านไฟล์ภาพไม่ได้"}), 400
    try:
        return jsonify({"predictions": predict_image(img), "model": MODEL_NAME, "device": DEVICE})
    except Exception:
        log.exception("prediction failed")
        return jsonify({"error": "ไม่สามารถวิเคราะห์รูปภาพได้"}), 500


if __name__ == "__main__":
    # ค่าเริ่มต้นรับเฉพาะเครื่องตัวเอง ถ้าเปิดสู่เครือข่ายให้ตั้ง FOODLEN_AI_TOKEN ด้วย ใน Docker/Render ใช้ gunicorn แทน
    app.run(host=os.environ.get("HOST", "127.0.0.1"), port=int(os.environ.get("PORT", "5000")), debug=False)
