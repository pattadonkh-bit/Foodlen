"""แปลงโมเดล ResNet50 (.pt) เป็น ONNX เพื่อให้บริการ AI รันด้วย onnxruntime โดยไม่ต้องใช้ PyTorch
(ใช้หน่วยความจำน้อยกว่ามาก เหมาะกับเซิร์ฟเวอร์แพ็กเกจฟรี 512 MB) และตรวจว่าผลตรงกับโมเดลเดิม

รัน (ในสภาพแวดล้อมที่มี torch torchvision onnx onnxruntime):
    python tools/export_onnx.py
ได้ไฟล์ models/resnet50_thfood101.onnx
"""
import json
from pathlib import Path

import numpy as np
import onnxruntime as ort
import torch
import torch.nn as nn
from torchvision import models

BASE = Path(__file__).resolve().parent.parent
PT = BASE / "models" / "resnet50_thfood101.pt"
CLASSES = BASE / "models" / "classes_thfood101.json"
OUT = BASE / "models" / "resnet50_thfood101.onnx"


def main():
    n = len(json.loads(CLASSES.read_text(encoding="utf-8"))["classes"])
    model = models.resnet50(weights=None)
    model.fc = nn.Linear(model.fc.in_features, n)
    model.load_state_dict(torch.load(PT, map_location="cpu", weights_only=True))
    model.eval()

    dummy = torch.randn(1, 3, 224, 224)
    torch.onnx.export(model, dummy, str(OUT), input_names=["input"], output_names=["logits"],
                      dynamic_axes={"input": {0: "batch"}, "logits": {0: "batch"}}, opset_version=17, dynamo=False)

    # ตรวจความตรงกัน: ค่า logits ของ ONNX ต้องใกล้เคียงโมเดลเดิม และอันดับ 1 ตรงกัน
    sess = ort.InferenceSession(str(OUT), providers=["CPUExecutionProvider"])
    x = torch.randn(4, 3, 224, 224)
    with torch.no_grad():
        ref = model(x).numpy()
    got = sess.run(None, {"input": x.numpy()})[0]
    print(f"saved {OUT} ({OUT.stat().st_size / 2**20:.1f} MB)")
    print("max abs diff:", float(np.abs(ref - got).max()), "| top1 equal:", bool((ref.argmax(1) == got.argmax(1)).all()))


if __name__ == "__main__":
    main()
