package com.example.demo.service;

import java.time.LocalDate;
import java.util.List;

/**
 * แถวของเด็กหนึ่งคนในตารางภาพรวม
 *
 * @param meals7         จำนวนมื้อที่บันทึกใน 7 วันล่าสุด
 * @param red7           จำนวนมื้อไฟแดงใน 7 วันล่าสุด
 * @param redPct         สัดส่วนไฟแดง (%) ใน 7 วัน (null ถ้าไม่มีมื้อ)
 * @param lastMealDate   วันที่บันทึกมื้อล่าสุดภายใน 30 วัน (null ถ้าไม่มี)
 * @param daysSinceMeal  จำนวนวันนับจากมื้อล่าสุด (null ถ้าไม่มีบันทึกใน 30 วัน)
 * @param reasons        เหตุผลที่ทำให้ได้ระดับความเสี่ยงนี้ (แสดงเป็นป้ายในตาราง)
 */
public record ClassRow(
        ChildOverview ov,
        int meals7,
        int red7,
        Integer redPct,
        LocalDate lastMealDate,
        Integer daysSinceMeal,
        Risk risk,
        List<String> reasons) {

    /** ไม่ได้บันทึกมื้อตั้งแต่ 3 วันขึ้นไป (หรือไม่มีบันทึกเลยใน 30 วัน) */
    public boolean isNotLogged() {
        return daysSinceMeal == null || daysSinceMeal >= 3;
    }

    /** ค่าที่ใช้เรียงตามภาวะโภชนาการ: z-score (ไม่มี = ค่ากลาง) */
    public double getZ() {
        return ov.status() == null ? 0 : ov.status().z();
    }
}
