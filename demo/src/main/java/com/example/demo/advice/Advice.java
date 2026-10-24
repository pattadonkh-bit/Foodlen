package com.example.demo.advice;

import java.util.List;

/**
 * ผลประเมินมื้ออาหารหนึ่งมื้อ
 *
 * @param energyKcal      พลังงานของมื้อ (หลังคูณสัดส่วน) หรือ null ถ้าเมนูไม่มีข้อมูล
 * @param shareOfDaily    พลังงานของมื้อ / พลังงานที่ควรได้รับต่อวัน หรือ null ถ้าประเมินไม่ได้
 * @param dailyTargetKcal พลังงานอ้างอิงต่อวันของเด็ก หรือ null ถ้าอายุนอกช่วง 6-12 ปี
 */
public record Advice(
        Level level,
        Double energyKcal,
        Double shareOfDaily,
        Integer dailyTargetKcal,
        List<String> messages,
        List<String> firedRules) {
}
