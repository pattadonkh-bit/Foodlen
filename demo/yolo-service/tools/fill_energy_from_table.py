"""เติมพลังงาน (kcal ต่อหน่วยบริโภค) ให้เมนูใน menu_nutrition.csv จากตารางแคลอรี่อาหารสาธารณะ

แหล่งข้อมูล: https://hdmall.co.th/health-tools/food-calorie-table.md
  (ตารางระบุว่าเมนูอาหาร 648 รายการเป็น "ค่าประมาณต่อ 1 จาน/ถ้วย/ชิ้น" และอ้างฐานข้อมูลสถาบันโภชนาการ มหาวิทยาลัยมหิดล
   เป็นแหล่งทุติยภูมิ ไม่ใช่ฐานข้อมูล Thai FCD ต้นฉบับ จึงตั้ง status = provisional)

หลักการ
  - จับคู่เฉพาะ "แถวเมนูอาหาร" (ไม่ใช่แถววัตถุดิบ INMU ต่อ 100 กรัม) ด้วยชื่อแถวที่ตรงทุกตัวอักษร (ตาม MAPPING ด้านล่าง
    ซึ่งผู้พัฒนาเลือกเอง) ถ้าเมนูในชุดข้อมูลกว้างกว่าแถวในตาราง (เช่น "แกงจืด" -> แกงจืดตำลึงหมูสับ) จะระบุชื่อแถวไว้ใน serving_th
  - ไม่เติมค่าโปรตีน/ไขมัน/คาร์โบไฮเดรต (ตารางไม่มีค่าต่อหน่วยบริโภคของเมนู)
  - เมนูที่ไม่มีแถวที่ตรงชัดเจน (เช่น ข้าวเหนียวมะม่วง ผัดกะปิ หอยทอด) คงเป็น no_data

รัน:  python yolo-service/tools/fill_energy_from_table.py --table <ไฟล์ food-calorie-table.md> --retrieved 2026-10-23
"""
import argparse
import csv
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
CSV_PATH = HERE.parent.parent / "src" / "main" / "resources" / "data" / "menu_nutrition.csv"

# รหัสเมนู -> ชื่อแถวในตาราง (ต้องตรงทุกตัวอักษรหลังตัดช่องว่าง/จุลภาค)
MAPPING = {
    "KhaoPhatAmerikan": "ข้าวผัดอเมริกัน",
    "PhatSeeEw": "ก๋วยเตี๋ยวเส้นใหญ่ผัดซีอิ๊วใส่ไข่",
    "KrapoPlaNamDang": "กระเพาะปลา",
    "SaiKrokIsan": "ไส้กรอกอีสาน",
    "PhatPrikGang": "ผัดพริกแกงหมูหน่อไม้",
    "KangChued": "แกงจืดตำลึงหมูสับ",
    "KhaiTun": "ไข่ตุ๋น",
    "Roti": "โรตี ใส่นมข้นและน้ำตาล",
    "NamPrikNum": "น้ำพริกหนุ่ม",
    "PhatThaiHoKhai": "ผัดไทยห่อไข่",
    "NamPrikOng": "น้ำพริกอ่อง",
    "KhaoChae": "ข้าวแช่",
    "KhaoKlukKapi": "ข้าวคลุกกะปิ",
    "PhatKana": "ผัดคะน้าปลาเค็ม",
    "TomKha": "ต้มข่าไก่",
    "YamWoonSen": "ยำวุ้นเส้น",
    "PlaPhao": "ปลาเผา",
    "PhatPakBungFaiDaeng": "ผัดผักบุ้งไฟแดง",
    "TaoHooThot": "เต้าหู้ทอด",
    "YamHoi": "ยำหอยแมลงภู่",
    "SukiHaeng": "สุกี้แห้งทะเล",
    "KungThot": "กุ้งทอดกระเทียมพริกไทย",
    "KhanomCheeb": "ขนมจีบหมู",
    "KuaitiaoKuaKai": "ก๋วยเตี๋ยวคั่วไก่",
    "KhaoMokKai": "ข้าวหมกไก่",
    "SaiUa": "ไส้อั่ว",
    "YamPlaDukFu": "ยำปลาดุกฟู",
    "KhaoSuai": "ข้าวสวย",
    "KhaoTom": "ข้าวต้มทรงเครื่อง",
    "KhaoMuDaeng": "ข้าวหมูแดง",
    "Palo": "พะโล้",
    "RatNa": "ก๋วยเตี๋ยวเส้นใหญ่ราดหน้าหมู",
    "Yentafo": "เย็นตาโฟ",
    "PlaMuekNguengManao": "ปลาหมึกนึ่งมะนาว",
    "KaengPa": "แกงป่าไก่",
    "Chok": "โจ๊กหมู",
    "KhanomCheenNamNgiao": "ขนมจีนน้ำเงี้ยว",
    "KaengSom": "แกงส้มปลาช่อนผักบุ้ง",
    "KhanomCheen": "ขนมจีนน้ำยา",
    "KaengLueang": "แกงเหลืองมะละกอกุ้ง",
    "KungChaeNamPla": "กุ้งแช่น้ำปลา",
    "MuKrop": "หมูกรอบ",
    "KhaiChiao": "ไข่เจียว",
    "KungObWunsen": "กุ้งอบวุ้นเส้น",
    "PhatCha": "ปลาหมึกผัดฉ่า",
    "ThotManKung": "ทอดมันกุ้ง",
    "Sate": "หมูสะเต๊ะ",
    "Lap": "ลาบหมู",
    "KhaoSoi": "ข้าวซอยไก่",
    "KaoLao": "เกาเหลาลูกชิ้นน้ำ",
    "ThotManPla": "ทอดมันปลากราย",
    "MuYang": "คอหมูย่าง",
    "SomTam": "ส้มตำ ไม่ใส่ถั่ว",
    "KhaoPhat": "ข้าวผัดกุ้งใส่ไข่",
}


def norm(s):
    return re.sub(r"[\s,]", "", s)


def parse_dish_rows(path):
    rows = []
    for line in open(path, encoding="utf-8"):
        if not line.startswith("| ") or line.startswith("| ---"):
            continue
        parts = [p.strip() for p in line.strip().strip("|").rsplit("|", 2)]
        if len(parts) != 3 or "INMU" in parts[0]:
            continue
        name, amount, kcal = parts
        try:
            rows.append((name, amount, float(kcal.replace(",", ""))))
        except ValueError:
            continue
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--table", required=True)
    ap.add_argument("--retrieved", required=True, help="วันที่ดึงตาราง เช่น 2026-10-23")
    args = ap.parse_args()

    dish = parse_dish_rows(args.table)
    index = {}
    for name, amount, kcal in dish:
        index.setdefault(norm(name), []).append((name, amount, kcal))

    with open(CSV_PATH, encoding="utf-8", newline="") as f:
        rd = csv.DictReader(f)
        header = rd.fieldnames
        menus = list(rd)

    audit, filled = [], 0
    for m in menus:
        target = MAPPING.get(m["code"])
        if not target:
            continue
        if m["energy_kcal"]:
            continue  # ไม่ทับค่าที่มีอยู่แล้ว
        hits = index.get(norm(target), [])
        if len(hits) != 1:
            raise SystemExit(f"จับคู่ {m['code']} -> '{target}' ไม่ได้ (พบ {len(hits)} แถว)")
        name, amount, kcal = hits[0]
        same = norm(name) == norm(m["name_th"])
        m["serving_th"] = amount.replace("1 ", "1 ", 1) + ("" if same else f" ({name})")
        m["energy_kcal"] = f"{kcal:g}"
        m["source"] = (f"hdmall.co.th ตารางแคลอรี่อาหาร (ค่าประมาณต่อหน่วย อ้างฐานข้อมูลสถาบันโภชนาการ มหิดล) "
                       f"ดึงเมื่อ {args.retrieved}")
        m["status"] = "provisional"
        audit.append((m["code"], m["name_th"], name, amount, f"{kcal:g}"))
        filled += 1

    for m in menus:
        for k, v in m.items():
            assert "," not in v and "\n" not in v, (m["code"], k, v)
    with open(CSV_PATH, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=header, lineterminator="\n")
        w.writeheader()
        w.writerows(menus)
    with open(HERE / "energy_rows_used.csv", "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["code", "name_th", "table_row", "amount", "kcal"])
        w.writerows(audit)
    total = sum(1 for m in menus if m["energy_kcal"])
    print(f"เติมใหม่ {filled} เมนู; มีพลังงานรวม {total} จาก {len(menus)} เมนู -> {CSV_PATH}")


if __name__ == "__main__":
    main()
