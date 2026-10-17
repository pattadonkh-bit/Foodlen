
from transformers import pipeline
from pathlib import Path

print("กำลังโหลด AI จำแนกอาหาร...")

classifier = pipeline(
    "image-classification",
    model="nateraw/food"
)

image_path = Path(
    r"C:\Users\PC\Downloads\chickenfriedrice-9-800x1200.jpg"
)

if not image_path.exists():
    print("ไม่พบรูปอาหาร กรุณาตรวจสอบตำแหน่งไฟล์")
else:
    results = classifier(str(image_path), top_k=5)

    print("\nผลการจำแนกอาหาร:")
    for item in results:
        print(
            f"{item['label']} - "
            f"{item['score'] * 100:.2f}%"
        )
