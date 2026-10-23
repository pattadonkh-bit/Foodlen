"""เพิ่มเมนูที่ "เลือกเอง" ได้ (AI ยังจำแนกได้แค่ 101 เมนูเดิม) ลงใน menu_nutrition.csv: เครื่องดื่ม นม ผลไม้ ขนม อาหารเช้า/ฟาสต์ฟู้ด และอาหารจานหลักที่เด็กกินบ่อย

*** ค่าโภชนาการทั้งหมดเป็นการคำนวณของผู้พัฒนาจากตารางแคลอรี่อาหาร https://hdmall.co.th/health-tools/food-calorie-table.md
    (อ้างฐานข้อมูลสถาบันโภชนาการ มหิดล; แหล่งทุติยภูมิ ไม่ใช่ Thai FCD ต้นฉบับ) ไม่ใช่ค่าที่ตรวจสอบแล้ว ***

พลังงาน (ช่อง status)
  provisional : ใช้ค่าจากแถว "เมนูอาหาร" ของตารางตรงๆ (1 หน่วยบริโภคตามที่ตารางระบุ)
  estimated   : ผู้พัฒนาคำนวณเอง เช่น ค่าต่อ 100 กรัม/มล. (แถว INMU) คูณปริมาณที่สมมติ (ระบุไว้ในช่องหน่วยบริโภค) หรือคูณหลายหน่วยจากแถวเมนู
โปรตีน ไขมัน คาร์โบไฮเดรต น้ำตาล
  ใช้สัดส่วนสารอาหารต่อกิโลแคลอรีของแถว INMU ที่คล้ายกัน (หรือผสมหลายแถวตามสัดส่วนพลังงาน) ปรับให้ได้พลังงานเท่ากับของเมนู (วิธีเดียวกับ fill_macros.py)
  น้ำตาลใส่เฉพาะเมื่อแถวที่ใช้ทุกแถวมีค่าน้ำตาลในตาราง และค่าที่ได้ไม่เกินคาร์โบไฮเดรต (บางแถวในตารางมีน้ำตาลมากกว่าคาร์โบไฮเดรตซึ่งเป็นไปไม่ได้ จึงเว้นว่าง)

เมนูพวกนี้ไม่มีคลาสในโมเดล AI จึงเลือกได้เฉพาะ "เลือกเมนูเอง" (ถ้าจะให้ AI จำแนกได้ต้องมีภาพฝึกของเมนูนั้น)

รัน:  python yolo-service/tools/add_menus.py --table <food-calorie-table.md> --retrieved 2026-10-24
"""
import argparse
import csv
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
CSV_PATH = HERE.parent.parent / "src" / "main" / "resources" / "data" / "menu_nutrition.csv"

# ชื่อแถว INMU ที่ใช้ซ้ำบ่อย
RICE = "ข้าวเจ้า, สุก"
STICKY = "ข้าวเหนียว, นึ่ง"
STIR = "ผัดเผ็ดไก่"
EGG_FRY = "ไข่ไก่, เจียว (เติมน้ำปลา)"
WUNSEN = "วุ้นเส้น (หลายยี่ห้อ), แช่ 15 นาที ลวก 3 นาที"
SOUP = "แกงซุปไก่"
BREAD = "ขนมปังแซนวิช"
FLOUR = "แป้งสาลี"
OIL = "น้ำมันถั่วเหลือง"
SUGAR = "น้ำตาลทรายแดง"
CONDENSED = "นมข้น, หวาน"
SHERBET = "ไอศครีมเชอร์เบต, รสต่างๆ"
SAUSAGE = "ไส้กรอก, หมู, ทอด"
MACARONI = "มักกะโรนี, หมู"
COOKIE = "คุ้กกี้, รสเนย"
CAKE = "เค้ก, เนย"

# (code, ชื่อไทย, ชื่ออังกฤษ, พลังงาน, สารอาหาร, tags, หมู่อาหาร)
#   พลังงาน: ("dish", ชื่อแถวเมนู, จำนวนหน่วย)  หรือ  ("inmu", ชื่อแถว INMU, กรัมหรือมล. ที่สมมติ, ข้อความหน่วยบริโภค)
#   สารอาหาร: None = ใช้แถว INMU เดียวกับพลังงาน, หรือ [(ชื่อแถว INMU, สัดส่วนพลังงาน), ...]
MENUS = [
    # ---- นมและเครื่องดื่ม ----
    ("MilkPlain", "นมจืด", "Plain milk", ("inmu", "นมพาสเจอร์ไรส์, รสธรรมชาติ, ต่อ 100 มล.", 200, "1 กล่อง (200 มล.)"), None, "", "milk"),
    ("MilkLowFat", "นมพร่องมันเนย", "Low-fat milk", ("inmu", "นมพร่องมันเนย, พาสเจอร์ไรส์, รสธรรมชาติ, ต่อ 100 มล.", 200, "1 กล่อง (200 มล.)"), None, "", "milk"),
    ("MilkChoc", "นมช็อกโกแลต", "Chocolate milk", ("inmu", "นมยูเอชที, รสช็อกโกแลต, ต่อ 100 มล.", 200, "1 กล่อง (200 มล.)"), None, "sweet", "milk"),
    ("MilkSweet", "นมรสหวาน", "Sweetened milk", ("inmu", "นมพาสเจอร์ไรส์, รสหวาน, ต่อ 100 มล.", 200, "1 กล่อง (200 มล.)"), None, "sweet", "milk"),
    ("DrinkYoghurt", "นมเปรี้ยว", "Drinking yoghurt", ("inmu", "นมเปรี้ยว, พร้อมดื่ม, พาสเจอร์ไรส์, รสต่างๆ ต่อ 100 มล.", 160, "1 ขวด (160 มล.)"), None, "sweet", "milk"),
    ("Yoghurt", "โยเกิร์ต", "Plain yoghurt", ("inmu", "โยเกิร์ต, รสธรรมชาติ", 125, "1 ถ้วย (125 กรัม)"), None, "", "milk"),
    ("SoyMilk", "นมถั่วเหลือง (ไม่หวาน)", "Unsweetened soy milk", ("dish", "นมถั่วเหลือง (ไม่มีน้ำตาล)", 1), [("นมถั่วเหลือง, ยูเอชที, ต่อ 100 มล.", 1)], "", "protein"),
    ("Ovaltine", "โอวัลติน", "Ovaltine", ("dish", "โอวัลติน", 1), [("เครื่องดื่มรสช็อกโกแลต, ผง", 1)], "sweet", "milk"),
    ("HotChocolate", "ช็อกโกแลตร้อน", "Hot chocolate", ("dish", "ช็อกโกแลตร้อน", 1), [("เครื่องดื่มรสช็อกโกแลต, ผง", 1)], "sweet", "milk"),
    ("OrangeJuice", "น้ำส้มคั้น", "Orange juice", ("dish", "น้ำส้มคั้น", 1), [("น้ำส้ม 100%, ต่อ 100 มล.", 1)], "sweet", "fruit"),
    ("GuavaJuice", "น้ำฝรั่ง (กล่อง)", "Guava juice", ("dish", "น้ำฝรั่ง", 1), [("น้ำฝรั่ง 25%, ต่อ 100 มล.", 1)], "sweet", "fruit"),
    ("Cola", "น้ำอัดลม (โค้ก/เป๊ปซี่)", "Cola", ("dish", "น้ำโค้ก, น้ำเป๊ปซี่", 1), [(SUGAR, 1)], "sweet", ""),
    ("ThaiIcedTea", "ชาเย็น", "Thai iced tea", ("dish", "ชาเย็น", 1), [(CONDENSED, 0.5), (SUGAR, 0.5)], "sweet", ""),
    ("IcedLemonTea", "ชามะนาว", "Iced lemon tea", ("dish", "ชามะนาว", 1), [(SUGAR, 1)], "sweet", ""),
    ("LimeJuice", "น้ำมะนาว", "Sweet lime juice", ("dish", "น้ำมะนาว", 1), [(SUGAR, 1)], "sweet", ""),
    ("PandanDrink", "น้ำใบเตย", "Pandan drink", ("dish", "น้ำใบเตย", 1), [(SUGAR, 1)], "sweet", ""),
    ("RoselleDrink", "น้ำกระเจี๊ยบ", "Roselle drink", ("dish", "น้ำกระเจี๊ยบ", 1), [("น้ำกระเจี้ยบ, ต่อ 100 มล.", 1)], "sweet", ""),
    ("SugarCaneJuice", "น้ำอ้อย", "Sugar cane juice", ("dish", "น้ำอ้อย", 1), [(SUGAR, 1)], "sweet", ""),
    ("CoconutWater", "น้ำมะพร้าว", "Coconut water", ("dish", "น้ำมะพร้าว", 1), [("น้ำมะพร้าว, ต่อ 100 มล.", 1)], "", ""),
    ("CornMilk", "น้ำนมข้าวโพด", "Corn milk", ("dish", "น้ำนมข้าวโพด", 1), [("น้ำนมข้าวโพด, ต่อ 100 มล.", 1)], "sweet", ""),
    # ---- ผลไม้ ----
    ("Banana", "กล้วยหอม", "Banana (Hom)", ("dish", "กล้วยหอม", 1), [("กล้วยหอม, สุก", 1)], "", "fruit"),
    ("BananaNamWa", "กล้วยน้ำว้า", "Banana (Nam Wa)", ("inmu", "กล้วยน้ำว้า, สุก", 60, "1 ลูก (เนื้อ ~60 กรัม)"), None, "", "fruit"),
    ("Mango", "มะม่วงสุก", "Ripe mango", ("dish", "มะม่วงสุก", 1), [("มะม่วงน้ำดอกไม้, สุก", 1)], "", "fruit"),
    ("Orange", "ส้มเขียวหวาน", "Tangerine", ("inmu", "ส้ม, เขียวหวาน", 120, "1 ลูก (~120 กรัม)"), None, "", "fruit"),
    ("Watermelon", "แตงโม", "Watermelon", ("inmu", "แตงโม, เนื้อแดง", 150, "1 ชิ้น (~150 กรัม)"), None, "", "fruit"),
    ("Pineapple", "สับปะรด", "Pineapple", ("inmu", "สับปะรด, พันธุ์ต่างๆ", 100, "1 ถ้วยเล็ก (~100 กรัม)"), None, "", "fruit"),
    ("Papaya", "มะละกอสุก", "Ripe papaya", ("inmu", "มะละกอ, สุก", 150, "1 ถ้วย (~150 กรัม)"), None, "", "fruit"),
    ("Apple", "แอปเปิล", "Apple", ("inmu", "แอปเปิ้ล, แดง", 150, "1 ลูก (~150 กรัม)"), None, "", "fruit"),
    ("Guava", "ฝรั่ง", "Guava", ("inmu", "ฝรั่ง รวมสายพันธุ์", 150, "ครึ่งผล (~150 กรัม)"), None, "", "fruit"),
    ("Longan", "ลำไย", "Longan", ("inmu", "ลำไย", 80, "10 เม็ด (~80 กรัม)"), None, "", "fruit"),
    ("Grapes", "องุ่น", "Grapes", ("inmu", "องุ่นแดง", 100, "1 กำมือ (~100 กรัม)"), None, "", "fruit"),
    ("Rambutan", "เงาะ", "Rambutan", ("inmu", "เงาะ", 100, "5 ผล (~100 กรัม)"), None, "", "fruit"),
    ("Lychee", "ลิ้นจี่", "Lychee", ("inmu", "ลิ้นจี่", 100, "5 ผล (~100 กรัม)"), None, "", "fruit"),
    # ---- ไข่ เนื้อสัตว์ ----
    ("BoiledEgg", "ไข่ต้ม", "Boiled egg", ("dish", "ไข่ต้ม", 1), [("ไข่ไก่, ต้ม, 10 นาที", 1)], "", "protein"),
    ("FriedEgg", "ไข่ดาว", "Fried egg", ("dish", "ไข่ดาว", 1), [(EGG_FRY, 1)], "fried", "protein"),
    ("SaltedEgg", "ไข่เค็ม", "Salted egg", ("dish", "ไข่เค็ม", 1), [("ไข่เค็ม, ไข่เป็ด", 1)], "", "protein"),
    ("PaloEgg", "ไข่พะโล้", "Eggs in five-spice stew", ("dish", "ไข่พะโล้", 1), [("ต้มพะโล้, รวม", 1)], "", "protein"),
    ("GrilledChickenLeg", "ไก่ย่าง (น่อง)", "Grilled chicken drumstick", ("dish", "ไก่ย่าง", 1), [("ไก่, ย่าง", 1)], "", "protein"),
    ("GrilledPorkSkewer", "หมูปิ้ง", "Grilled pork skewer", ("dish", "หมูปิ้ง", 1), [("หมูแดดเดียว, ปิ้ง", 1)], "", "protein"),
    ("GrilledMeatball", "ลูกชิ้นปิ้ง", "Grilled meatball skewer", ("dish", "ลูกชิ้นปิ้ง", 1), [("ลูกชิ้น, วัว, ปิ้ง", 1)], "", "protein"),
    ("FriedMackerel", "ปลาทูทอด", "Fried mackerel", ("dish", "ปลาทูทอด", 1), [("ปลาทู, สด, ทอด", 1)], "fried", "protein"),
    ("FriedSausage", "ไส้กรอกทอด", "Fried sausage", ("inmu", SAUSAGE, 60, "2 ชิ้น (~60 กรัม)"), None, "fried", "protein"),
    ("FriedChickenHip", "ไก่ทอด (สะโพก)", "Fried chicken thigh", ("dish", "ไก่ KFC สะโพก", 1), [("ไก่, สะโพก, ทอด", 1)], "fried", "protein"),
    # ---- อาหารจานหลัก ----
    ("KhaoKhaiJiao", "ข้าวไข่เจียว", "Rice with omelette", ("dish", "ข้าวไข่เจียว", 1), [(RICE, 0.5), (EGG_FRY, 0.5)], "fried", "starch;protein"),
    ("KhaoPhatMuKhai", "ข้าวผัดหมูใส่ไข่", "Fried rice with pork and egg", ("dish", "ข้าวผัดหมูใส่ไข่", 1), [("ข้าวผัดหมู", 1)], "", "starch;protein"),
    ("KhaoKaprawMu", "ข้าวผัดกะเพราหมู", "Stir-fried basil pork with rice", ("dish", "ข้าวผัดกะเพราหมู", 1), [("ข้าวราดไก่ผัดใบกะเพรา", 1)], "", "starch;protein"),
    ("KhaoPhatSausage", "ข้าวผัดไส้กรอก", "Fried rice with sausage", ("dish", "ข้าวผัดไส้กรอก", 1), [("ข้าวผัดหมู", 1)], "", "starch;protein"),
    ("KhaoMuThot", "ข้าวหมูทอด", "Rice with fried pork", ("dish", "ข้าวหมูทอด", 1), [("ข้าวหมูทอด", 1)], "fried", "starch;protein"),
    ("KhaoMuThotKratiam", "ข้าวหมูทอดกระเทียม", "Rice with garlic fried pork", ("dish", "ข้าวหมูทอดกระเทียม", 1), [("ข้าวหมูทอด", 1)], "fried", "starch;protein"),
    ("KhaoManKaiThot", "ข้าวมันไก่ทอด", "Chicken rice (fried chicken)", ("dish", "ข้าวมันไก่ทอด", 1), [("ข้าวมัน, ไก่ทอด", 1)], "fried", "starch;protein"),
    ("KhaoKaengKariGai", "ข้าวแกงกะหรี่ไก่", "Chicken curry rice", ("dish", "ข้าวแกงกะหรี่ไก่", 1), [("แกงกะหรี่, ไก่", 0.5), (RICE, 0.5)], "", "starch;protein"),
    ("KhaoRatPhatPakMu", "ข้าวราดผัดผักใส่หมู", "Rice with stir-fried vegetables and pork", ("dish", "ข้าวราดผัดผักใส่หมู", 1), [(STIR, 0.5), (RICE, 0.5)], "", "starch;protein;vegetable"),
    ("KhaoTomKhao", "ข้าวต้ม (ข้าวขาว)", "Plain rice porridge", ("dish", "ข้าวต้ม (ข้าวขาว)", 1), [(RICE, 1)], "", "starch"),
    ("KhaoNiewNueng", "ข้าวเหนียวนึ่ง", "Steamed sticky rice", ("dish", "ข้าวเหนียวนึ่ง", 1), [(STICKY, 1)], "", "starch"),
    ("KhaoNiewMuThot", "ข้าวเหนียวหมูทอด", "Sticky rice with fried pork", ("dish", "ข้าวเหนียวหมูทอด", 1), [(STICKY, 0.5), ("หมู, สันนอก, ทอด", 0.5)], "fried", "starch;protein"),
    ("BoiledCorn", "ข้าวโพดต้ม", "Boiled corn", ("dish", "ข้าวโพดต้ม", 1), [("ข้าวโพด, พันธุ์ข้าวเหนียว, ต้ม", 1)], "", "starch"),
    ("KuaitiaoMuHaeng", "ก๋วยเตี๋ยวเส้นเล็กหมูแห้ง", "Dry noodles with pork", ("dish", "ก๋วยเตี๋ยวเส้นเล็กหมูแห้ง", 1), [("เส้นเล็ก, หมู, แห้ง", 1)], "", "starch;protein"),
    ("KuaitiaoLukchinNuea", "ก๋วยเตี๋ยวเส้นหมี่ลูกชิ้นเนื้อ", "Noodle soup with beef balls", ("dish", "ก๋วยเตี๋ยวเส้นหมี่ลูกชิ้นเนื้อ", 1), [("เส้นหมี่, ลูกชิ้นเนื้อวัว, น้ำ", 1)], "", "starch;protein"),
    ("BamiKiaoMuDaeng", "บะหมี่น้ำเกี๊ยวหมูแดง", "Egg noodle soup with wonton and red pork", ("dish", "บะหมี่น้ำเกี๊ยวหมูแดง", 1), [("บะหมี่, หมูแดง, แห้ง", 1)], "", "starch;protein"),
    ("BamiHaengMuDaeng", "บะหมี่แห้งหมูแดง", "Dry egg noodles with red pork", ("dish", "บะหมี่แห้งหมูแดง", 1), [("บะหมี่, หมูแดง, แห้ง", 1)], "", "starch;protein"),
    ("SpaghettiBakedChicken", "สปาเกตตีไก่อบ", "Spaghetti with baked chicken", ("dish", "สปาเกตตีไก่อบ", 1), [(MACARONI, 1)], "", "starch;protein"),
    ("Yakisoba", "ยากิโซบะ", "Yakisoba", ("dish", "ยากิโซบะ", 1), [("เส้นหมี่, ผัดซีอิ้ว", 1)], "", "starch"),
    ("PhatMacaroniMu", "ผัดมักกะโรนีหมู", "Stir-fried macaroni with pork", ("dish", "ผัดมักกะโรนีหมู", 1), [(MACARONI, 1)], "", "starch;protein"),
    ("InstantNoodle", "บะหมี่กึ่งสำเร็จรูป (1 ก้อน)", "Instant noodles", ("dish", "บะหมี่กึ่งสำเร็จรูป", 1), [("บะหมี่กึ่งสำเร็จรูป, พร้อมเครื่องปรุง, รสหมูสับ", 1)], "fried", "starch"),
    ("PhatWoonSenKhai", "ผัดวุ้นเส้นใส่ไข่", "Stir-fried glass noodles with egg", ("dish", "ผัดวุ้นเส้นใส่ไข่", 1), [(WUNSEN, 0.5), (EGG_FRY, 0.25), (STIR, 0.25)], "", "starch;protein"),
    ("Gyoza", "เกี๊ยวซ่า", "Gyoza", ("dish", "เกี๊ยวซ่า", 1), [("ขนมจีบ, หมูสับ", 1)], "", "starch;protein"),
    ("SalapaoMu", "ซาลาเปาไส้หมู", "Pork steamed bun", ("dish", "ซาลาเปาไส้หมู", 1), [(BREAD, 0.6), ("หมู, สับ, ต้ม", 0.4)], "", "starch;protein"),
    ("PhatKhingGai", "ไก่ผัดขิง", "Stir-fried chicken with ginger", ("dish", "ไก่ผัดขิง", 1), [("ผัดพริกขิงไก่ใส่ถั่วฝักยาว", 1)], "", "protein;vegetable"),
    ("PhatKhingMu", "หมูผัดขิง", "Stir-fried pork with ginger", ("dish", "หมูผัดขิง", 1), [("ผัดพริกขิงไก่ใส่ถั่วฝักยาว", 1)], "", "protein;vegetable"),
    ("PhatPakRuamMu", "ผัดผักรวมหมู", "Stir-fried mixed vegetables with pork", ("dish", "ผัดผักรวมหมู", 1), [(STIR, 1)], "", "vegetable;protein"),
    ("KaengLiang", "แกงเลียง", "Mixed vegetable soup (Kaeng Liang)", ("dish", "แกงเลียง", 1), [("แกงส้มผักรวม", 1)], "", "vegetable"),
    ("KaengJuedWoonSen", "แกงจืดวุ้นเส้น", "Clear soup with glass noodles", ("dish", "แกงจืดวุ้นเส้น", 1), [(SOUP, 1)], "", "starch;protein"),
    ("KaengJuedKhaiJiao", "แกงจืดไข่เจียว", "Clear soup with omelette", ("dish", "แกงจืดไข่เจียว", 1), [(SOUP, 0.6), (EGG_FRY, 0.4)], "", "protein"),
    # ---- ฟาสต์ฟู้ด เบเกอรี่ ----
    ("PizzaHawaiian", "พิซซ่าฮาวายเอี้ยน (1 ชิ้น)", "Hawaiian pizza slice", ("dish", "พิซซ่าฮาวายเอี้ยน", 1), [("พิซซ่า, ฮาวายเอี้ยน, ชนิดหนา", 1)], "", "starch;protein"),
    ("PorkBurger", "แฮมเบอร์เกอร์หมู", "Pork burger", ("dish", "แฮมเบอร์เกอร์หมู", 1), [("แฮมเบอร์เกอร์, เนื้อ", 1)], "fried", "starch;protein"),
    ("ChickenSandwich", "แซนด์วิชไก่", "Chicken sandwich", ("dish", "แซนด์วิชไก่", 1), [("แซนวิช, ไก่", 1)], "", "starch;protein"),
    ("TunaSandwich", "แซนด์วิชทูน่า", "Tuna sandwich", ("dish", "แซนด์วิชทูน่า", 1), [("แซนวิช, ปลา", 1)], "", "starch;protein"),
    ("Donut", "โดนัท", "Donut", ("dish", "โดนัท", 1), [("โดนัท, ฮันนี่ดิพ", 1)], "sweet", "starch"),
    ("Croissant", "ครัวซอง", "Croissant", ("dish", "ครัวซอง", 1), [(BREAD, 0.5), ("เนยสด, ชนิดจืด", 0.5)], "", "starch"),
    ("WhiteBread", "ขนมปังปอนด์ (1 แผ่น)", "White bread slice", ("dish", "ขนมปังปอนด์", 1), [(BREAD, 1)], "", "starch"),
    ("BreadCustard", "ขนมปังสังขยา", "Bread with custard", ("dish", "ขนมปังสังขยา", 1), [(BREAD, 0.5), ("สังขยา", 0.5)], "sweet", "starch"),
    ("BreadSausage", "ขนมปังไส้กรอก", "Sausage bun", ("dish", "ขนมปังไส้กรอก", 1), [(BREAD, 0.5), (SAUSAGE, 0.5)], "", "starch;protein"),
    ("BreadPorkFloss", "ขนมปังไส้หมูหยอง", "Pork floss bun", ("dish", "ขนมปังไส้หมูหยอง", 1), [(BREAD, 0.6), ("หมูหยอง", 0.4)], "", "starch;protein"),
    ("CornFlakes", "คอร์นเฟลก (1 ถ้วย ไม่รวมนม)", "Corn flakes", ("dish", "คอร์นเฟลก", 1), [("ข้าวโพดอบกรอบ, รสต่างๆ", 1)], "", "starch"),
    # ---- ขนมและของว่าง ----
    ("ThongYip", "ทองหยิบ", "Thong Yip", ("dish", "ทองหยิบ", 1), [("ทองหยิบ", 1)], "sweet", ""),
    ("KhanomKrok", "ขนมครก (1 คู่)", "Coconut pancake", ("dish", "ขนมครก", 1), [("ขนมครก", 1)], "sweet", ""),
    ("KhanomChan", "ขนมชั้น", "Layered cake (Khanom Chan)", ("dish", "ขนมชั้น", 1), [("ขนมชั้น", 1)], "sweet", ""),
    ("KhanomThuai", "ขนมถ้วย", "Steamed coconut cup cake", ("dish", "ขนมถ้วย", 1), [("ขนมชั้น", 1)], "sweet", ""),
    ("KluaiThot", "กล้วยทอด (กล้วยแขก)", "Fried banana", ("dish", "กล้วยทอด (กล้วยแขก)", 1), [("กล้วย, ทอด", 1)], "fried", "fruit"),
    ("KluaiBuatChi", "กล้วยบวชชี", "Banana in coconut milk", ("dish", "กล้วยบวชชี", 1), [("กล้วยบวชชี", 1)], "sweet", "fruit"),
    ("BuaLoi", "บัวลอย", "Sweet rice balls in coconut milk", ("dish", "บัวลอย", 1), [("ขนมบัวลอย", 1)], "sweet", ""),
    ("LotChong", "ลอดช่องน้ำกะทิ", "Lod chong", ("dish", "ลอดช่องน้ำกะทิ", 1), [("ลอดช่องน้ำกะทิ", 1)], "sweet", ""),
    ("Chaokuay", "เฉาก๊วย", "Grass jelly with syrup", ("dish", "เฉาก๊วย", 1), [("น้ำเฉาก๊วย", 1)], "sweet", ""),
    ("YoungCoconutJelly", "วุ้นมะพร้าวอ่อน", "Young coconut jelly", ("dish", "วุ้นมะพร้าวอ่อน", 1), [("วุ้นมะพร้าวอ่อน", 1)], "sweet", ""),
    ("MungBeanSweet", "ถั่วเขียวต้มน้ำตาล", "Sweet mung bean soup", ("dish", "ถั่วเขียวต้มน้ำตาล", 1), [("ถั่วเขียว, เมล็ด, ต้มน้ำตาล", 1)], "sweet", ""),
    ("KhaoNiewMoon", "ข้าวเหนียวมูนกะทิ", "Sweet sticky rice", ("dish", "ข้าวเหนียวมูนกะทิ", 1), [("ข้าวเหนียวมูล", 1)], "sweet", "starch"),
    ("KhaoNiewDam", "ข้าวเหนียวดำ", "Black sticky rice dessert", ("dish", "ข้าวเหนียวดำ", 1), [("ข้าวเหนียวดำ, นึ่ง", 1)], "sweet", "starch"),
    ("ButterCookie", "คุ้กกี้เนย", "Butter cookie", ("dish", "คุ้กกี้เนย", 1), [(COOKIE, 1)], "sweet", ""),
    ("ChocChipCookie", "คุ้กกี้ช็อกโกแลตชิป", "Chocolate chip cookie", ("dish", "คุ้กกี้ช็อกโกแลตชิป", 1), [("คุกกี้, ผสมช็อคโกแลตชิฟ", 1)], "sweet", ""),
    ("ChocolateCake", "เค้กช็อกโกแลต", "Chocolate cake", ("dish", "เค้กช็อกโกแลต", 1), [(CAKE, 1)], "sweet", ""),
    ("ButterCake", "เค้กเนย", "Butter cake", ("dish", "เค้กเนย", 1), [(CAKE, 1)], "sweet", ""),
    ("Brownie", "บราวนี่", "Brownie", ("dish", "บราวนี่", 1), [(CAKE, 1)], "sweet", ""),
    ("IceCreamCoconut", "ไอศกรีมกะทิ (1 ก้อน)", "Coconut ice cream scoop", ("dish", "ไอศกรีมกะทิ", 1), [(SHERBET, 1)], "sweet", ""),
    ("IceCreamVanilla", "ไอศกรีมวานิลลา (1 ก้อน)", "Vanilla ice cream scoop", ("dish", "ไอศกรีมวานิลลา", 1), [(SHERBET, 1)], "sweet", "milk"),
    ("IceCreamChocolate", "ไอศกรีมช็อกโกแลต (1 ก้อน)", "Chocolate ice cream scoop", ("dish", "ไอศกรีมช็อกโกแลต", 1), [(SHERBET, 1)], "sweet", "milk"),
    ("PaThongKo", "ปาท่องโก๋", "Patongko (fried dough)", ("dish", "ปาท่องโก๋", 1), [(FLOUR, 0.6), (OIL, 0.4)], "fried", "starch"),
    ("CurryPuff", "กะหรี่ปั๊บ", "Curry puff", ("dish", "กะหรี่ปั๊บ", 1), [(FLOUR, 0.5), (OIL, 0.3), ("ไก่, อก, รวมหนัง, ทอด", 0.2)], "fried", "starch;protein"),
    ("KhanomBuengWan", "ขนมเบื้องไทยไส้หวาน", "Thai crispy pancake (sweet)", ("dish", "ขนมเบื้องไทยไส้หวาน", 1), [("ขนมเบื้อง, ครีม, ไส้หวาน", 1)], "sweet", ""),
    ("PotatoChips", "มันฝรั่งทอดกรอบ (ซองเล็ก)", "Potato chips", ("inmu", "มันฝรั่ง, ทอดกรอบ, รสต่างๆ", 30, "1 ซองเล็ก (~30 กรัม)"), None, "fried", ""),
    ("ShrimpChips", "ข้าวเกรียบกุ้ง", "Prawn crackers", ("inmu", "ข้าวเกรียบกุ้ง, ทอด", 20, "1 ถุงเล็ก (~20 กรัม)"), None, "fried", ""),
]


def norm(s):
    return re.sub(r"[\s,()]", "", s)


def parse_table(path):
    dish, inmu = {}, {}
    for line in open(path, encoding="utf-8"):
        if not line.startswith("| ") or line.startswith("| ---"):
            continue
        parts = [p.strip() for p in line.strip().strip("|").rsplit("|", 2)]
        if len(parts) != 3:
            continue
        cell, amount, kcal = parts
        try:
            kcal = float(kcal.replace(",", ""))
        except ValueError:
            continue
        if "INMU" in cell:
            name = cell[:cell.index("INMU")].strip()
            name = re.sub(r"^.*?title=\"[^\"]*\">", "", name)
            macro = cell[cell.index("INMU") + 4:]
            vals = {"kcal": kcal}
            for key, label in (("p", "โปรตีน"), ("f", "ไขมัน"), ("c", "คาร์บ"), ("s", "น้ำตาล")):
                m = re.search(label + r" ([\d.,]+) ก\.", macro)
                if m:
                    vals[key] = float(m.group(1).replace(",", ""))
            if all(k in vals for k in ("p", "f", "c")) and kcal > 0:
                inmu.setdefault(name, []).append(vals)
        else:
            dish.setdefault(norm(cell), []).append((cell, amount, kcal))
    return dish, inmu


def one(table, name, kind):
    hits = table.get(name, [])
    if len(hits) != 1:
        raise SystemExit(f"{kind} '{name}' พบ {len(hits)} แถว")
    return hits[0]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--table", required=True)
    ap.add_argument("--retrieved", required=True)
    args = ap.parse_args()
    dish, inmu = parse_table(args.table)

    with open(CSV_PATH, encoding="utf-8", newline="") as f:
        rd = csv.DictReader(f)
        header = rd.fieldnames
        menus = list(rd)
    existing = {m["code"] for m in menus}
    existing_names = {m["name_th"] for m in menus}
    assert len({m[0] for m in MENUS}) == len(MENUS), "รหัสซ้ำในรายการใหม่"

    audit, added = [], 0
    for code, name_th, name_en, energy, macros, tags, groups in MENUS:
        if code in existing:
            continue
        if name_th in existing_names:
            raise SystemExit(f"ชื่อซ้ำกับเมนูเดิม: {name_th}")
        # --- พลังงาน ---
        if energy[0] == "dish":
            _, rowname, qty = energy
            row_name, amount, kcal_row = one(dish, norm(rowname), "แถวเมนู")
            kcal = kcal_row * qty
            serving = amount if qty == 1 else f"{qty:g} x {amount}"
            status = "provisional" if qty == 1 else "estimated"
            how = f"{row_name} [{amount}] {kcal_row:g} kcal x {qty:g}"
            default_macros = None
        else:
            _, rowname, grams, serving = energy
            r = one(inmu, rowname, "แถว INMU")
            kcal = r["kcal"] * grams / 100.0
            status = "estimated"
            how = f"{rowname} [100 กรัม/มล.] {r['kcal']:g} kcal x {grams:g}"
            default_macros = [(rowname, 1)]
        kcal = round(kcal)
        # --- สารอาหาร ---
        parts = macros if macros is not None else default_macros
        if parts is None:
            raise SystemExit(f"{code}: ไม่มี proxy ของสารอาหาร")
        assert abs(sum(s for _, s in parts) - 1) < 1e-9, code
        p = f_ = c = s = 0.0
        sugar_ok = True
        used = []
        for rname, share in parts:
            r = one(inmu, rname, "แถว INMU")
            k = kcal * share / r["kcal"]
            p += r["p"] * k
            f_ += r["f"] * k
            c += r["c"] * k
            if "s" in r:
                s += r["s"] * k
            else:
                sugar_ok = False
            used.append(f"{rname} ({share:g} ของพลังงาน)")
        if sugar_ok and s > c + 0.5:
            sugar_ok = False  # ค่าน้ำตาลในตารางบางแถวมากกว่าคาร์โบไฮเดรต (เป็นไปไม่ได้) จึงไม่ใช้
        src = ("เมนูเพิ่มเติม (เลือกเอง ไม่มีใน AI): พลังงานจากตารางแคลอรี่อาหาร hdmall.co.th อ้างฐานข้อมูลสถาบันโภชนาการ มหิดล "
               f"ดึงเมื่อ {args.retrieved}; สารอาหารคำนวณโดยผู้พัฒนาจากแถว INMU ที่คล้ายกัน ไม่ใช่ค่าของเมนูนี้โดยตรง วิธีคำนวณอยู่ใน new_menus.csv")
        menus.append({
            "code": code, "name_th": name_th, "name_en": name_en, "serving_th": serving.replace(",", ""),
            "energy_kcal": f"{kcal:d}", "protein_g": f"{p:.1f}", "fat_g": f"{f_:.1f}", "carb_g": f"{c:.1f}",
            "sugar_g": f"{s:.1f}" if sugar_ok else "", "tags": tags, "food_groups": groups,
            "source": src.replace(",", ";"), "status": status,
        })
        audit.append((code, name_th, serving, kcal, status, f"{p:.1f}", f"{f_:.1f}", f"{c:.1f}",
                      f"{s:.1f}" if sugar_ok else "", how, " + ".join(used)))
        added += 1

    for m in menus:
        for k, v in m.items():
            assert "," not in v and "\n" not in v, (m["code"], k, v)
    with open(CSV_PATH, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=header, lineterminator="\n")
        w.writeheader()
        w.writerows(menus)
    with open(HERE / "new_menus.csv", "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["code", "name_th", "serving", "energy_kcal", "status", "protein_g", "fat_g", "carb_g", "sugar_g",
                    "energy_how", "macro_rows"])
        w.writerows(audit)
    print(f"เพิ่ม {added} เมนู; รวมทั้งหมด {len(menus)} เมนู")


if __name__ == "__main__":
    main()
