"""เติมพลังงานแบบ "ประมาณ" ให้เมนูที่ยังไม่มีแถวตรงชื่อในตารางแคลอรี่อาหาร (ต่อจาก fill_energy_from_table.py)

*** ค่าที่ได้เป็นการประมาณของผู้พัฒนา ไม่ใช่ค่าที่วัดหรือค่าทางการของเมนูนั้น ***
ทุกค่าอ้างอิงได้กลับไปยังแถวในตารางแคลอรี่อาหาร https://hdmall.co.th/health-tools/food-calorie-table.md
(อ้างฐานข้อมูลสถาบันโภชนาการ มหาวิทยาลัยมหิดล) ด้วยวิธีใดวิธีหนึ่งต่อไปนี้ และตั้ง status = estimated

  dish : ใช้แถวเมนูอาหารที่คล้ายกัน (kcal ต่อหน่วยบริโภค) คูณจำนวนหน่วย  (เช่น หมูฮ้อง ~ พะโล้)
  inmu : ใช้แถววัตถุดิบ/อาหาร INMU (kcal ต่อ 100 กรัม) คูณน้ำหนักที่ "สมมติ" ของหนึ่งหน่วยบริโภค
  ผลรวมของหลายส่วนใช้กับเมนูที่ประกอบจากหลายอย่าง (เช่น ข้าวเหนียวมะม่วง = ข้าวเหนียวมูน + มะม่วงสุกครึ่งลูก)

น้ำหนักที่สมมติและเมนูที่ถือว่า "คล้ายกัน" เป็นการตัดสินใจของผู้พัฒนา ผู้เชี่ยวชาญควรตรวจก่อนใช้จริง

รัน:  python yolo-service/tools/fill_estimates.py --table <food-calorie-table.md> --retrieved 2026-10-23
"""
import argparse
import csv
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
CSV_PATH = HERE.parent.parent / "src" / "main" / "resources" / "data" / "menu_nutrition.csv"

# code -> (หน่วยบริโภคที่แสดง, [(ชนิด, ชื่อแถว, ตัวคูณหรือกรัม), ...])
EST = {
    "KhaoNaKai": ("1 จาน", [("dish", "ข้าวหน้าเป็ด", 1)]),
    "PoPiaThot": ("1 ชิ้น", [("dish", "เกี๊ยวกรอบ", 1)]),
    "LapKua": ("1 จาน", [("dish", "ลาบหมู", 1)]),
    "GaiPhatMedMamuangHimmapan": ("1 จาน (สมมติใส่เม็ดมะม่วง 5 ชิ้น)",
                                  [("dish", "ไก่ผัดขิง", 1), ("dish", "เมล็ดมะม่วงหิมพานต์ทอด", 5)]),
    "PhatPrikThaiDam": ("1 จาน", [("dish", "ผัดพริกแกงหมูหน่อไม้", 1)]),
    "PlaNuengSeeEw": ("1 ตัว", [("dish", "ปลานึ่ง", 1)]),
    "PhatKhanaeng": ("1 จาน", [("dish", "ผัดผักบุ้งไฟแดง", 1)]),
    "KhaiKrata": ("1 ฟอง", [("dish", "ไข่ดาวทรงเครื่อง", 1)]),
    "MuHong": ("1 ถ้วย", [("dish", "พะโล้", 1)]),
    "PlaMuekThot": ("1 ชิ้น", [("dish", "ปลาหมึกชุปแป้งทอด", 1)]),
    "KuaKling": ("1 จาน", [("dish", "ผัดพริกแกงหมูหน่อไม้", 1)]),
    "PlaNuengManao": ("1 ตัว", [("dish", "ปลานึ่ง", 1)]),
    "PlaMuekPhatNamDam": ("1 ถ้วย", [("dish", "ปลาหมึกผัดฉ่า", 1)]),
    "KiaoThot": ("1 ชิ้น", [("dish", "เกี๊ยวกรอบ", 1)]),
    "KangThotKratiam": ("1 ตัว", [("dish", "กุ้งทอดกระเทียมพริกไทย", 1)]),
    "PoPiaSod": ("1 ม้วน (สมมติ 60 กรัม)", [("inmu", "ปอเปี๊ยะสด", 60)]),
    "PhatPrikSod": ("1 จาน", [("dish", "กุ้งผัดพริกอ่อน", 1)]),
    "KaengHangLe": ("1 ถ้วย (สมมติ 150 กรัม)", [("inmu", "แกงฮังเล", 150)]),
    "PuNimThotKratiam": ("1 ตัว", [("dish", "กุ้งทอดกระเทียมพริกไทย", 1)]),
    "KalampliPhatNamPla": ("1 จาน", [("dish", "ผัดกะหล่ำปลีหมู", 1)]),
    "PhatKapi": ("1 จาน", [("dish", "ผัดพริกขิงหมูถั่วฝักยาว", 1)]),
    "HoiThot": ("1 จาน", [("dish", "หอยแมลงภู่ทอด", 1)]),
    "HoiCho": ("1 ชิ้น (สมมติ 50 กรัม)", [("inmu", "หอยจ้อ, ทอด ไก่หรือปู", 50)]),
    "HoiPhatNamPrikPhao": ("1 จาน", [("dish", "ผัดพริกขิงกุ้งกับถั่วฝักยาว", 1)]),
    "KaengKuaHoi": ("1 ถ้วย", [("dish", "แกงเขียวหวานไก่", 1)]),
    "KuaPrikKluea": ("1 จาน", [("dish", "ผัดพริกแกงหมูหน่อไม้", 1)]),
    "TaoJiaoLon": ("1 ถ้วย", [("dish", "หลนปูเค็ม", 1)]),
    "Kuaichap": ("1 ชาม (สมมติ 400 กรัม)", [("inmu", "ก๋วยจั๊บ", 400)]),
    "HoiNangrom": ("1 ตัว", [("dish", "หอยแมลงภู่อบหม้อดิน", 1)]),
    "KungThotSotMakham": ("1 ตัว", [("dish", "กุ้งทอดกระเทียมพริกไทย", 1)]),
    "PuPhatPhongKari": ("1 ถ้วย", [("dish", "ปลาหมึกผัดฉ่า", 1)]),
    "HoiKraengLuak": ("1 จาน (สมมติ 100 กรัม)", [("inmu", "หอยแครง, ลวก", 100)]),
    "HoMok": ("1 ถ้วยเล็ก", [("dish", "ไข่ตุ๋นทรงเครื่อง", 1)]),
    "PlaMuekPhatKaiKem": ("1 ถ้วย", [("dish", "ปลาหมึกผัดฉ่า", 1)]),
    "BaiLiangPhatKhai": ("1 จาน", [("dish", "ผัดผักกาดดองใส่ไข่", 1)]),
    "PuNueng": ("1 จาน (เนื้อปู สมมติ 150 กรัม)", [("inmu", "ปูทะเล, เนื้อ", 150)]),
    "KungPhao": ("1 ตัว (สมมติ 100 กรัม)", [("inmu", "กุ้งก้ามกราม, ไม่มีไข่, เผา", 100)]),
    "PlaThot": ("1 ตัว (เนื้อ สมมติ 150 กรัม)", [("inmu", "ปลานิล, ทอด", 150)]),
    "KhaoNiewMaMuang": ("1 จาน", [("dish", "ข้าวเหนียวมูนกะทิ", 1), ("dish", "มะม่วงสุก", 0.5)]),
}


def norm(s):
    return re.sub(r"[\s,()]", "", s)


def parse(path):
    dish, inmu = {}, {}
    for line in open(path, encoding="utf-8"):
        if not line.startswith("| ") or line.startswith("| ---"):
            continue
        parts = [p.strip() for p in line.strip().strip("|").rsplit("|", 2)]
        if len(parts) != 3:
            continue
        name, amount, kcal = parts
        try:
            k = float(kcal.replace(",", ""))
        except ValueError:
            continue
        if "INMU" in name:
            name = name[:name.index("INMU")].strip()
            name = re.sub(r"^.*?title=\"[^\"]*\">", "", name)
            inmu.setdefault(norm(name), []).append((name, amount, k))
        else:
            dish.setdefault(norm(name), []).append((name, amount, k))
    return dish, inmu


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--table", required=True)
    ap.add_argument("--retrieved", required=True)
    args = ap.parse_args()
    dish, inmu = parse(args.table)

    with open(CSV_PATH, encoding="utf-8", newline="") as f:
        rd = csv.DictReader(f)
        header = rd.fieldnames
        menus = list(rd)

    audit, filled = [], 0
    for m in menus:
        if m["energy_kcal"] or m["code"] not in EST:
            continue
        serving, parts = EST[m["code"]]
        total, desc = 0.0, []
        for kind, name, qty in parts:
            hits = (dish if kind == "dish" else inmu).get(norm(name), [])
            if len(hits) != 1:
                raise SystemExit(f"{m['code']}: แถว '{name}' พบ {len(hits)} แถว")
            row_name, amount, kcal = hits[0]
            if kind == "dish":
                total += kcal * qty
                desc.append(f"{row_name} [{amount}] {kcal:g} kcal x {qty:g}")
            else:
                total += kcal * qty / 100.0
                desc.append(f"{row_name} [100 กรัม] {kcal:g} kcal x {qty:g} กรัม")
        m["serving_th"] = serving
        m["energy_kcal"] = f"{total:.0f}"
        m["source"] = ("ประมาณโดยผู้พัฒนาจากตารางแคลอรี่อาหาร hdmall.co.th (อ้างฐานข้อมูลสถาบันโภชนาการ มหิดล) "
                       f"ดึงเมื่อ {args.retrieved} ไม่ใช่ค่าของเมนูนี้โดยตรง วิธีคำนวณอยู่ใน energy_estimates.csv")
        m["status"] = "estimated"
        audit.append((m["code"], m["name_th"], serving, f"{total:.0f}", " + ".join(desc)))
        filled += 1

    for m in menus:
        for k, v in m.items():
            assert "," not in v and "\n" not in v, (m["code"], k, v)
    with open(CSV_PATH, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=header, lineterminator="\n")
        w.writeheader()
        w.writerows(menus)
    with open(HERE / "energy_estimates.csv", "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["code", "name_th", "serving", "estimated_kcal", "how_calculated"])
        w.writerows(audit)
    missing = [m["code"] for m in menus if not m["energy_kcal"]]
    print(f"เติมประมาณ {filled} เมนู; ยังไม่มีพลังงาน {len(missing)} เมนู {missing}")


if __name__ == "__main__":
    main()
