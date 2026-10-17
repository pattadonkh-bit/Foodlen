
from flask import Flask, request, jsonify
from transformers import pipeline
from PIL import Image
from pathlib import Path
import tempfile
import os

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 10 * 1024 * 1024

# AI จำแนกประเภทอาหารที่เราทดสอบแล้ว
classifier = pipeline(
    "image-classification",
    model="nateraw/food"
)

# ข้อมูลโภชนาการโดยประมาณต่อหนึ่งหน่วยบริโภค
# ตัวเลขเป็นตัวอย่างสำหรับ Demo ไม่ใช่ค่าที่วัดจากรูปจริง
NUTRITION = {
    "fried_rice": {
        "display_name": "ข้าวผัด",
        "serving": "1 จาน (ประมาณ 300 กรัม)",
        "calories": 550,
        "protein": 18,
        "fat": 15,
        "carbohydrates": 80
    },
    "pizza": {
        "display_name": "พิซซ่า",
        "serving": "1 ชิ้น (ประมาณ 100 กรัม)",
        "calories": 266,
        "protein": 11,
        "fat": 10,
        "carbohydrates": 33
    },
    "hamburger": {
        "display_name": "แฮมเบอร์เกอร์",
        "serving": "1 ชิ้น",
        "calories": 295,
        "protein": 17,
        "fat": 12,
        "carbohydrates": 30
    },
    "sushi": {
        "display_name": "ซูชิ",
        "serving": "ประมาณ 100 กรัม",
        "calories": 150,
        "protein": 6,
        "fat": 3,
        "carbohydrates": 25
    },
    "spaghetti_bolognese": {
        "display_name": "สปาเกตตีโบโลเนส",
        "serving": "1 จาน",
        "calories": 400,
        "protein": 18,
        "fat": 12,
        "carbohydrates": 55
    }
}


@app.get("/health")
def health():
    return jsonify({
        "status": "ok",
        "service": "Food Classification and Nutrition Demo"
    })


@app.post("/detect")
def detect():
    if "image" not in request.files:
        return jsonify({"error": "ไม่พบไฟล์ image"}), 400

    image_file = request.files["image"]

    if not image_file.filename:
        return jsonify({"error": "กรุณาเลือกภาพ"}), 400

    if (
        not image_file.mimetype
        or not image_file.mimetype.startswith("image/")
    ):
        return jsonify({"error": "ไฟล์ต้องเป็นรูปภาพ"}), 400

    temp_path = None

    try:
        suffix = Path(image_file.filename).suffix or ".jpg"

        with tempfile.NamedTemporaryFile(
            suffix=suffix, delete=False
        ) as temp_file:
            temp_path = temp_file.name
            image_file.save(temp_path)

        # เปิดภาพและจำแนกอาหาร
        with Image.open(temp_path) as img:
            img = img.convert("RGB")
            predictions = classifier(img, top_k=5)

        best = predictions[0]
        food_label = best["label"].lower().strip()
        confidence = float(best["score"])

        # ค้นหาข้อมูลโภชนาการที่มีอยู่ใน Dictionary
        nutrition = NUTRITION.get(food_label)

        if nutrition:
            food_result = {
                "name": food_label,
                "displayName": nutrition["display_name"],
                "confidence": round(confidence, 4),
                "nutrition": nutrition,
                "nutritionAvailable": True
            }
        else:
            food_result = {
                "name": food_label,
                "displayName": food_label.replace("_", " ").title(),
                "confidence": round(confidence, 4),
                "nutrition": None,
                "nutritionAvailable": False
            }

        return jsonify({
            "food": food_result,
            "predictions": [
                {
                    "name": item["label"],
                    "confidence": round(
                        float(item["score"]), 4
                    )
                }
                for item in predictions
            ],
            "note": (
                "ผลจำแนกเป็นการคาดเดาจากภาพ "
                "และโภชนาการเป็นค่าประมาณตามหน่วยบริโภคที่กำหนด"
            )
        })

    except Exception:
        app.logger.exception("Food classification failed")
        return jsonify({
            "error": "ไม่สามารถวิเคราะห์รูปภาพได้"
        }), 500

    finally:
        if temp_path and os.path.exists(temp_path):
            os.remove(temp_path)


if __name__ == "__main__":
    app.run(
        host="127.0.0.1",
        port=5000,
        debug=False
    )
