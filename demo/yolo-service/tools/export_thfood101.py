"""ส่งออกโมเดล THFOOD-101 (ResNet50) เข้าบริการ AI ของ Foodlen และสร้างตารางเมนู

ทำ 3 อย่าง
  1. คัดลอกน้ำหนักโมเดล -> yolo-service/models/resnet50_thfood101.pt
  2. เขียน yolo-service/models/classes_thfood101.json (รายชื่อคลาสเรียงตามลำดับของโมเดล + ชื่อไทย/อังกฤษ)
  3. สร้าง src/main/resources/data/menu_nutrition.csv สำหรับทุกเมนู:
       - เมนูที่เคยมีค่าพลังงานอยู่แล้ว (10 เมนูเดิม) ใช้ค่าเดิมจาก tools/menu_seed_10menus.csv
       - เมนูใหม่: ค่าโภชนาการว่าง (status = no_data) ต้องกรอกจากแหล่งที่ตรวจสอบได้ (เช่น Thai FCD)
       - tags (fried/sweet) และหมู่อาหาร ของเมนูใหม่กำหนดอัตโนมัติจากคำในชื่อเมนู (heuristic) เป็นค่าเบื้องต้น

รัน (ใน Foodlen/demo):  python yolo-service/tools/export_thfood101.py --train-project "D:/คู่มือ/food-nutrition-project"
"""
import argparse
import csv
import json
import re
import shutil
from pathlib import Path

HERE = Path(__file__).resolve().parent
YOLO = HERE.parent
DEMO = YOLO.parent

# รหัสเมนูเดิม -> รหัสใหม่ (ชื่ออังกฤษของ THFOOD-100 ตัดเลขนำหน้า)
OLD_TO_NEW = {
    "FriedChicken": "KaiThot",
    "GaengKeawWan": "KaengKhiaoWan",
    "KaoManGai": "KhaoManKai",
    "KhaoNiewMaMuang": "KhaoNiewMaMuang",
    "KuayTeowReua": "KuaitiaoRuea",
    "PadThai": "PhatThai",
    "PhatKaphrao": "PhatKaprao",
    "Somtam": "SomTam",
    "StewedPorkLeg": "KhaoKhaMu",
    "TomYumGoong": "TomYam",
}
# หน่วยบริโภคที่ต้องระบุให้ตรงกับความหมายของค่าเดิม (เมนูใหม่เป็นชื่อกว้างกว่าเดิม)
SERVING_NOTE = {
    "PhatKaprao": "1 จาน (ผัดกะเพราหมูราดข้าว)",
    "TomYam": "1 ถ้วย (ต้มยำกุ้ง)",
    "KaengKhiaoWan": "1 ถ้วย (แกงเขียวหวานไก่ ไม่รวมข้าว)",
}

# heuristic จากคำในชื่อไทย (ค่าเบื้องต้น ต้องให้ผู้เชี่ยวชาญตรวจ)
FRIED = ["ทอด", "กรอบ"]
STARCH = ["ข้าว", "ก๋วยเตี๋ยว", "ขนมจีน", "วุ้นเส้น", "ซีอิ๊ว", "ผัดไทย", "สุกี้", "ราดหน้า", "เย็นตาโฟ", "ก๋วยจั๊บ", "โรตี", "โจ๊ก"]
PROTEIN = ["ไก่", "หมู", "เนื้อ", "ปลา", "กุ้ง", "หอย", "ปู", "ไข่", "เต้าหู้", "ไส้กรอก", "ลาบ", "ทอดมัน", "สะเต๊ะ",
           "พะโล้", "จีบ", "เกี๊ยว", "ห่อหมก", "คั่วกลิ้ง", "ไส้อั่ว", "ปอเปี๊ยะ", "แกงฮังเล"]
VEG = ["ผัก", "คะน้า", "แขนง", "กะหล่ำ", "ใบเหลียง", "บุ้ง", "ส้มตำ", "แกงจืด", "ต้มยำ", "ต้มข่า", "แกงส้ม", "แกงป่า",
       "แกงเขียวหวาน", "ยำ", "แกงเหลือง"]


def heuristic(thai):
    tags = ["fried"] if any(k in thai for k in FRIED) else []
    groups = []
    if any(k in thai for k in STARCH):
        groups.append("starch")
    if any(k in thai for k in PROTEIN):
        groups.append("protein")
    if any(k in thai for k in VEG):
        groups.append("vegetable")
    return tags, groups


def code_of(folder):
    return re.sub(r"^\d+_", "", folder)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--train-project", required=True, help="โฟลเดอร์ food-nutrition-project")
    ap.add_argument("--run", default="thfood101_resnet50")
    args = ap.parse_args()
    proj = Path(args.train_project)
    run = proj / "results" / args.run
    ds = json.loads((proj / "data" / "thfood100_256" / "classes.json").read_text(encoding="utf-8"))
    metrics = json.loads((run / "metrics.json").read_text(encoding="utf-8"))
    classes = ds["classes"]
    assert [c["folder"] for c in classes] == metrics["classes"], "ลำดับคลาสในโมเดลกับชุดข้อมูลไม่ตรงกัน"

    # 1) โมเดล
    models = YOLO / "models"
    models.mkdir(exist_ok=True)
    shutil.copy2(run / "best.pt", models / "resnet50_thfood101.pt")

    # 2) classes
    labels = []
    for c in classes:
        thai = "ข้าวเหนียวมะม่วง" if c.get("extra") else c["thai"]
        labels.append({"code": code_of(c["folder"]), "folder": c["folder"], "thai": thai,
                       "english": c["english"] if not c.get("extra") else "Khao Niao Mamuang"})
    (models / "classes_thfood101.json").write_text(json.dumps({
        "model": "resnet50", "architecture": "resnet50", "num_classes": len(labels),
        "classes": [c["folder"] for c in classes], "labels": labels,
        "source": "fine-tuned on THFOOD-100 (Theera-Ampornpunt & Treepong, 2024; non-commercial research/education) "
                  "+ KhaoNiewMaMuang from siamtech-food10",
        "test_accuracy": round(metrics["test_accuracy"], 4), "test_top5_accuracy": round(metrics["test_top5_accuracy"], 4),
        "test_macro_f1": round(metrics["test_macro_f1"], 4), "note": "single run, official THFOOD-100 test split"},
        ensure_ascii=False, indent=2), encoding="utf-8")

    # 3) ตารางเมนู
    seed = {r["code"]: r for r in csv.DictReader(open(HERE / "menu_seed_10menus.csv", encoding="utf-8-sig"))}
    new_to_old = {v: k for k, v in OLD_TO_NEW.items()}
    header = ["code", "name_th", "name_en", "serving_th", "energy_kcal", "protein_g", "fat_g", "carb_g", "sugar_g",
              "tags", "food_groups", "source", "status"]
    rows = []
    for lab in labels:
        code = lab["code"]
        old = seed.get(new_to_old.get(code, ""))
        if old:  # เมนูเดิม: คงค่าที่เคยมี แต่ใช้ชื่อไทยของชุดข้อมูลใหม่ (ถ้าความหมายต่างกันให้ระบุในหน่วยบริโภค)
            serving = SERVING_NOTE.get(new_to_old[code], old["serving_th"])
            rows.append([code, lab["thai"], lab["english"], serving, old["energy_kcal"], old["protein_g"], old["fat_g"],
                         old["carb_g"], old["sugar_g"], old["tags"], old["food_groups"], old["source"], old["status"]])
        else:
            tags, groups = heuristic(lab["thai"])
            rows.append([code, lab["thai"], lab["english"], "1 หน่วยบริโภค (ยังไม่ระบุ)", "", "", "", "", "",
                         ";".join(tags), ";".join(groups),
                         "ยังไม่มีค่าที่ตรวจสอบได้ (กรอกจาก Thai FCD); tag/หมู่อาหารจากคำในชื่อเมนู (heuristic)", "no_data"])
    out = DEMO / "src" / "main" / "resources" / "data" / "menu_nutrition.csv"
    with open(out, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(header)
        w.writerows(rows)
    with_energy = sum(1 for r in rows if r[4])
    print(f"เมนู {len(rows)} รายการ (มีพลังงาน {with_energy}) -> {out}")
    print(f"โมเดล -> {models / 'resnet50_thfood101.pt'}")


if __name__ == "__main__":
    main()
