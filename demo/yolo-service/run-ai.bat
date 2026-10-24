@echo off
rem เริ่มบริการ AI (พอร์ต 5000) ด้วย Python ใน .venv ของโฟลเดอร์นี้
rem ครั้งแรกให้สร้าง .venv และติดตั้งไลบรารี (ต้องมี Python 3.10-3.12):
rem   py -3.10 -m venv .venv
rem   .venv\Scripts\python.exe -m pip install -r requirements.txt
cd /d "%~dp0"
if not exist ".venv\Scripts\python.exe" (
  echo ยังไม่มี .venv — สร้างด้วย:  py -3.10 -m venv .venv  แล้ว  .venv\Scripts\python.exe -m pip install -r requirements.txt
  exit /b 1
)
".venv\Scripts\python.exe" app.py
