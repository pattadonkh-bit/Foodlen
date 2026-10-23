"""เติมโปรตีน/ไขมัน/คาร์โบไฮเดรต (กรัมต่อหน่วยบริโภค) ให้เมนูใน menu_nutrition.csv

*** ค่าที่ได้เป็น "การคำนวณของผู้พัฒนา" ไม่ใช่ค่าที่วัดหรือค่าทางการของเมนูนั้น ***

แหล่งข้อมูล: แถวป้าย INMU ในตารางแคลอรี่อาหาร https://hdmall.co.th/health-tools/food-calorie-table.md
  (ค่าต่อ 100 กรัม จากฐานข้อมูลสถาบันโภชนาการ มหาวิทยาลัยมหิดล ผ่านเว็บ hdmall ซึ่งเป็นแหล่งทุติยภูมิ ไม่ใช่ Thai FCD ต้นฉบับ)

วิธีคำนวณ (ทำให้สอดคล้องกับพลังงานที่เติมไว้แล้วในไฟล์)
  สำหรับแต่ละเมนู เลือกแถว INMU ที่คล้ายกัน 1 แถวหรือผสมหลายแถวตามสัดส่วนพลังงาน (MAPPING ด้านล่าง ผู้พัฒนาเลือกเอง)
  สารอาหารของแถว i ต่อหน่วยบริโภค = (สารอาหารต่อ 100 กรัม / กิโลแคลอรีต่อ 100 กรัม ของแถว) x (พลังงานเมนู x สัดส่วน)
  กล่าวคือใช้ "สัดส่วนสารอาหารต่อกิโลแคลอรี" ของแถวที่คล้ายกัน แล้วปรับให้ได้พลังงานเท่ากับค่าของเมนู
  จึงไม่ต้องรู้น้ำหนักต่อจาน แต่คุณภาพขึ้นกับว่าเมนูคล้ายแถวที่เลือกแค่ไหน

ข้อจำกัด: เมนูที่ไม่มีแถว INMU ที่ตรงตัว (เช่น ผัดฉ่า ผัดพริกแกง) ใช้แถวตัวแทน/ผสมวัตถุดิบ ผู้เชี่ยวชาญควรตรวจก่อนใช้จริง

รัน:  python yolo-service/tools/fill_macros.py --table <food-calorie-table.md> --retrieved 2026-10-23
"""
import argparse
import csv
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
CSV_PATH = HERE.parent.parent / "src" / "main" / "resources" / "data" / "menu_nutrition.csv"

STIR = "ผัดเผ็ดไก่"
# แถว "ผัดเผ็ดถั่วฝักยาว" ในตารางมีโปรตีน 21 กรัมต่อ 100 กรัม (สูงผิดปกติสำหรับจานผัก) จึงไม่ใช้ ใช้ผัดเผ็ดไก่แทน
VEG_STIR = "ผัดเผ็ดไก่"
SQUID_RAW = "ปลาหมึกกระดอง, สด"
SHRIMP_RAW = "กุ้งกุลาดำ, เนื้อ, สด"
OIL = "น้ำมันถั่วเหลือง"
FLOUR = "แป้งสาลี"
WUNSEN = "วุ้นเส้น (หลายยี่ห้อ), แช่ 15 นาที ลวก 3 นาที"
FRIED_SHRIMP = [(SHRIMP_RAW, 0.5), (OIL, 0.25), (FLOUR, 0.25)]
SQUID_STIR = [(SQUID_RAW, 0.4), (STIR, 0.6)]
FRIED_RICE_PORK = "ข้าวผัดหมู"
SOMTAM = "ส้มตำ, ไทย"
BOILED_RICE = "ข้าวเจ้า, สุก"
NOODLE_SEEEW = "ก๋วยเตี๋ยวเส้นใหญ่, ผัดซี่อิ้ว, หมู"
EGG_FRY = "ไข่ไก่, เจียว (เติมน้ำปลา)"
EGG_STEAM = "ไข่ไก่, ตุ๋น (เติมน้ำและน้ำปลา)"
LAP = "ลาบอีสาน (ลาบเนื้อ)"
SPRING_WRAP = "แผ่นเกี๊ยวทอด ไม่มีหมู"
PALO = "ต้มพะโล้, รวม"
SHRIMP_ROLL = "ปอเปี๊ยะ, กุ้ง"

# รหัสเมนู -> [(ชื่อแถว INMU ตรงตัวอักษร, สัดส่วนพลังงาน), ...]  สัดส่วนรวม = 1
MAPPING = {
    "KhaoPhatAmerikan": [(FRIED_RICE_PORK, 1)],
    "KhaoNaKai": [("ข้าวหน้าเป็ดย่าง", 1)],
    "PoPiaThot": [(SPRING_WRAP, 0.5), (SHRIMP_ROLL, 0.5)],
    "PhatSeeEw": [(NOODLE_SEEEW, 1)],
    "KrapoPlaNamDang": [("กระเพาะปลา", 1)],
    "SaiKrokIsan": [("ไส้กรอก, อีสาน, ทอด", 1)],
    "LapKua": [(LAP, 1)],
    "GaiPhatMedMamuangHimmapan": [("ผัดพริกขิงไก่ใส่ถั่วฝักยาว", 0.6), ("มะม่วงหิมพานต์, เมล็ดแห้ง, คั่ว", 0.4)],
    "PhatPrikGang": [(STIR, 1)],
    "PhatPrikThaiDam": [(STIR, 1)],
    "PlaNuengSeeEw": [("ปลาช่อน, นึ่ง", 1)],
    "PhatKhanaeng": [(VEG_STIR, 1)],
    "KhaiKrata": [(EGG_FRY, 1)],
    "KangChued": [("แกงซุปไก่", 1)],
    "KuaitiaoRuea": [("เส้นหมี่, เนื้อสด-เปื่อย, น้ำ", 1)],
    "KhaiTun": [(EGG_STEAM, 1)],
    "Roti": [("โรตี", 1)],
    "MuHong": [(PALO, 1)],
    "NamPrikNum": [("พริกหนุ่ม, ย่าง", 1)],
    "PhatThaiHoKhai": [("ก๋วยเตี๋ยว, ผัดไทย", 1)],
    "NamPrikOng": [("น้ำพริกอ่อง", 1)],
    "KhaoChae": [(BOILED_RICE, 1)],
    "KhaoKlukKapi": [(FRIED_RICE_PORK, 1)],
    "KaengKhiaoWan": [("แกงเขียวหวาน, ไก่", 1)],
    "PlaMuekThot": [("ปลาหมึกผสมแป้งสาลี, อบกรอบ", 1)],
    "KuaKling": [(STIR, 1)],
    "PhatKana": [(VEG_STIR, 1)],
    "PlaNuengManao": [("ปลาช่อน, นึ่ง", 1)],
    "TomKha": [("ต้มข่าไก่", 1)],
    "YamWoonSen": [("ยำรวมมิตรทะเล", 1)],
    "PlaPhao": [("ปลาช่อน, ย่าง", 1)],
    "PlaMuekPhatNamDam": SQUID_STIR,
    "KiaoThot": [(SPRING_WRAP, 1)],
    "KangThotKratiam": FRIED_SHRIMP,
    "PhatPakBungFaiDaeng": [(VEG_STIR, 1)],
    "TaoHooThot": [("เต้าหู้, ทอด", 1)],
    "YamHoi": [("ยำรวมมิตรทะเล", 1)],
    "SukiHaeng": [(WUNSEN, 0.5), (STIR, 0.5)],
    "KungThot": FRIED_SHRIMP,
    "KhanomCheeb": [("ขนมจีบ, หมูสับ", 1)],
    "KuaitiaoKuaKai": [(NOODLE_SEEEW, 1)],
    "KalampliPhatNamPla": [(VEG_STIR, 1)],
    "KhaoMokKai": [("ข้าวหมก, ไก่", 1)],
    "PhatKapi": [(STIR, 1)],
    "SaiUa": [("ไส้อั่ว, ย่าง", 1)],
    "YamPlaDukFu": [("ปลาดุกอุย, ทอด", 1)],
    "HoiThot": [("หอยแมลงภู่ทอดใส่ไข่", 1)],
    "PhatKaprao": [("ข้าวราดไก่ผัดใบกะเพรา", 1)],
    "KhaoSuai": [(BOILED_RICE, 1)],
    "HoiCho": [("หอยจ้อ, ทอด ไก่หรือปู", 1)],
    "HoiPhatNamPrikPhao": [("หอยลาย, ผัด", 1)],
    "KhaoKhaMu": [("ข้าวขาหมู", 1)],
    "KaengKuaHoi": [("แกงหอยขม, ภาคกลาง", 1)],
    "KhaoTom": [("ข้าวต้มหมู", 1)],
    "KhaoMuDaeng": [("ข้าวหมูแดง", 1)],
    "Palo": [(PALO, 1)],
    "KuaPrikKluea": [(STIR, 1)],
    "RatNa": [("ก๋วยเตี๋ยวเส้นใหญ่, ราดหน้า, ไก่/หมู", 1)],
    "TaoJiaoLon": [("ผัดพริกขิงไก่ใส่ถั่วฝักยาว", 1)],
    "Yentafo": [("ก๋วยเตี๋ยวเส้นใหญ่, น้ำ, เย็นตาโฟ", 1)],
    "PlaMuekNguengManao": [(SQUID_RAW, 1)],
    "KaengPa": [("แกงป่าฟักทองใส่หมู", 1)],
    "Chok": [("โจ๊ก, หมู", 1)],
    "KhanomCheenNamNgiao": [("ขนมจีน, น้ำเงี้ยว, ภาคเหนือ", 1)],
    "Kuaichap": [("ก๋วยจั๊บ", 1)],
    "KaengSom": [("แกงส้มผักรวม", 1)],
    "HoiNangrom": [("หอยนางรม, ผัด", 1)],
    "KhanomCheen": [("ขนมจีน", 1)],
    "KaengLueang": [("แกงเหลืองใส่ปลา", 1)],
    "KungChaeNamPla": [(SHRIMP_RAW, 1)],
    "KungThotSotMakham": FRIED_SHRIMP,
    "MuKrop": [("หมูกรอบ (หมูสามชั้นทอดกรอบ)", 1)],
    "PuPhatPhongKari": [("ปูทะเล, เนื้อ", 0.4), (STIR, 0.6)],
    "KhaiChiao": [(EGG_FRY, 1)],
    "KungObWunsen": [(SHRIMP_RAW, 0.5), (WUNSEN, 0.5)],
    "PhatCha": SQUID_STIR,
    "HoiKraengLuak": [("หอยแครง, ลวก", 1)],
    "HoMok": [("ปลากราย, ขูด, ต้ม", 0.4), ("กะทิ, สด", 0.4), (EGG_STEAM, 0.2)],
    "PlaMuekPhatKaiKem": SQUID_STIR,
    "PhatThai": [("ก๋วยเตี๋ยว, ผัดไทย", 1)],
    "ThotManKung": [("ทอดมัน, กุ้ง", 1)],
    "Sate": [("หมูสะเต๊ะ", 1)],
    "KhaoManKai": [("ข้าวมัน, ไก่ต้ม", 1)],
    "KaiThot": [("ไก่, น่อง, ทอด", 1)],
    "Lap": [(LAP, 1)],
    "BaiLiangPhatKhai": [(VEG_STIR, 1)],
    "KhaoSoi": [("ข้าวซอยไก่", 1)],
    "KaoLao": [("เส้นหมี่, ลูกชิ้นเนื้อวัว, น้ำ", 1)],
    "ThotManPla": [("ทอดมัน, ปลา", 1)],
    "PuNueng": [("ปูทะเล, เนื้อ", 1)],
    "MuYang": [("หมู, คอ, ย่าง", 1)],
    "SomTam": [(SOMTAM, 1)],
    "KungPhao": [("กุ้งก้ามกราม, ไม่มีไข่, เผา", 1)],
    "KhaoPhat": [("ข้าวผัดทั่วไป, ไม่ระบุ", 1)],
    "TomYam": [("ต้มยำ, กุ้ง", 1)],
    "PlaThot": [("ปลานิล, ทอด", 1)],
    "KhaoNiewMaMuang": [("ข้าวเหนียวมูล", 0.8), ("มะม่วงน้ำดอกไม้, สุก", 0.2)],
    "PoPiaSod": [("ปอเปี๊ยะสด", 1)],
    "PhatPrikSod": [(STIR, 1)],
    "KaengHangLe": [("แกงฮังเล", 1)],
    "PuNimThotKratiam": [("ปูนิ่ม (ปูทะเล), ทั้งตัว, แช่แข็ง, ยี่ห้อ ARO", 0.5), (OIL, 0.25), (FLOUR, 0.25)],
}


def parse_inmu(path):
    rows = {}
    for line in open(path, encoding="utf-8"):
        if not line.startswith("| ") or line.startswith("| ---") or "INMU" not in line:
            continue
        parts = [p.strip() for p in line.strip().strip("|").rsplit("|", 2)]
        if len(parts) != 3:
            continue
        cell, amount, kcal = parts
        if amount != "100 กรัม":
            continue
        try:
            kcal = float(kcal.replace(",", ""))
        except ValueError:
            continue
        name = cell[:cell.index("INMU")].strip()
        name = re.sub(r"^.*?title=\"[^\"]*\">", "", name)
        macro = cell[cell.index("INMU") + 4:]
        vals = {}
        for key, label in (("p", "โปรตีน"), ("f", "ไขมัน"), ("c", "คาร์บ")):
            m = re.search(label + r" ([\d.,]+) ก\.", macro)
            if m:
                vals[key] = float(m.group(1).replace(",", ""))
        if len(vals) == 3 and kcal > 0:
            rows.setdefault(name, []).append({"kcal": kcal, **vals})
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--table", required=True)
    ap.add_argument("--retrieved", required=True)
    args = ap.parse_args()
    inmu = parse_inmu(args.table)

    with open(CSV_PATH, encoding="utf-8", newline="") as f:
        rd = csv.DictReader(f)
        header = rd.fieldnames
        menus = list(rd)

    audit, warnings = [], []
    for m in menus:
        parts = MAPPING.get(m["code"])
        if parts is None:
            raise SystemExit(f"ไม่มี mapping ของ {m['code']}")
        assert abs(sum(s for _, s in parts) - 1) < 1e-9, m["code"]
        energy = float(m["energy_kcal"])
        p = f_ = c = 0.0
        desc = []
        for name, share in parts:
            hits = inmu.get(name, [])
            if len(hits) != 1:
                raise SystemExit(f"{m['code']}: แถว INMU '{name}' พบ {len(hits)} แถว")
            r = hits[0]
            e = energy * share
            k = e / r["kcal"]
            p += r["p"] * k
            f_ += r["f"] * k
            c += r["c"] * k
            atwater = 4 * r["p"] + 9 * r["f"] + 4 * r["c"]
            if abs(atwater - r["kcal"]) > 0.3 * r["kcal"] + 15:
                warnings.append(f"{m['code']} แถว '{name}': kcal {r['kcal']:g} แต่ 4P+9F+4C = {atwater:.0f}")
            desc.append(f"{name} ({share:g} ของพลังงาน)")
        m["protein_g"] = f"{p:.1f}"
        m["fat_g"] = f"{f_:.1f}"
        m["carb_g"] = f"{c:.1f}"
        note = (f" | สารอาหารหลัก: คำนวณโดยผู้พัฒนาจากแถว INMU ต่อ 100 กรัม (hdmall.co.th) ปรับตามพลังงานเมนู "
                f"ดึงเมื่อ {args.retrieved} รายละเอียดใน macro_estimates.csv")
        if "สารอาหารหลัก:" not in m["source"]:
            m["source"] += note.replace(",", ";")
        audit.append((m["code"], m["name_th"], m["energy_kcal"], m["protein_g"], m["fat_g"], m["carb_g"],
                      f"{4 * p + 9 * f_ + 4 * c:.0f}", " + ".join(desc)))

    for m in menus:
        for k, v in m.items():
            assert "," not in v and "\n" not in v, (m["code"], k, v)
    with open(CSV_PATH, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=header, lineterminator="\n")
        w.writeheader()
        w.writerows(menus)
    with open(HERE / "macro_estimates.csv", "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["code", "name_th", "energy_kcal", "protein_g", "fat_g", "carb_g", "atwater_kcal", "inmu_rows"])
        w.writerows(audit)
    print(f"เติมสารอาหารหลัก {len(audit)} เมนู")
    for w_ in warnings:
        print("เตือน:", w_)


if __name__ == "__main__":
    main()
