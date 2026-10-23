#!/bin/sh
# เริ่มบริการ AI (พื้นหลัง, เฉพาะ 127.0.0.1) แล้วเริ่มเว็บ Java เป็นโปรเซสหลัก
(cd ai && exec gunicorn --workers 1 --threads 2 --timeout 120 --bind 127.0.0.1:5000 app:app) &
exec java -jar app.jar
