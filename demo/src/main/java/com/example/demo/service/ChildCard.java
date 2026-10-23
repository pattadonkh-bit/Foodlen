package com.example.demo.service;

import java.time.LocalDate;
import java.util.List;

import com.example.demo.advice.Level;

/**
 * การ์ดเด็กหนึ่งคนบนหน้าแรก (แดชบอร์ด)
 *
 * @param levelsToday  ไฟจราจรของมื้อที่บันทึกวันนี้ เรียงตามเวลา
 * @param meals7       จำนวนมื้อใน 7 วันล่าสุด
 * @param lastMealDate วันที่บันทึกมื้อล่าสุดภายใน 7 วัน (null ถ้าไม่มี)
 */
public record ChildCard(ChildOverview ov, List<Level> levelsToday, int meals7, LocalDate lastMealDate) {

    public int getMealsToday() {
        return levelsToday.size();
    }

    /** ไฟที่แย่ที่สุดของวันนี้ (null ถ้ายังไม่มีมื้อ) */
    public Level getWorstToday() {
        Level worst = null;
        for (Level l : levelsToday) {
            worst = worst == null ? l : Level.worst(worst, l);
        }
        return worst;
    }
}
