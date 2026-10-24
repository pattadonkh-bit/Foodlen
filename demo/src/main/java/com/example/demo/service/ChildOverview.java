package com.example.demo.service;

import com.example.demo.domain.Child;
import com.example.demo.domain.Measurement;
import com.example.demo.nutrition.StatusResult;

/**
 * สรุปข้อมูลเด็กสำหรับแสดงผลและประเมินมื้ออาหาร
 *
 * @param status         ผลภาวะโภชนาการจากการวัดล่าสุด (null ถ้าประเมินไม่ได้)
 * @param statusProblem  เหตุผลที่ประเมินไม่ได้ (null ถ้าประเมินได้)
 * @param dailyKcal      พลังงานอ้างอิงต่อวัน ณ อายุปัจจุบัน (null ถ้านอกช่วง 6-12 ปี)
 * @param ageMonths      อายุปัจจุบันเป็นเดือน
 */
public record ChildOverview(
        Child child,
        Measurement latest,
        StatusResult status,
        String statusProblem,
        Integer dailyKcal,
        int ageMonths) {

    /** ตัวอักษรแรกของชื่อ ใช้ในรูปโปรไฟล์วงกลม */
    public String getInitial() {
        String n = child.getName() == null ? "" : child.getName().trim();
        for (String prefix : new String[] {"น้อง", "เด็กชาย", "เด็กหญิง", "ด.ช.", "ด.ญ."}) {
            if (n.startsWith(prefix) && n.length() > prefix.length()) {
                n = n.substring(prefix.length()).trim();
                break;
            }
        }
        // ข้ามสระนำ (เ แ โ ใ ไ) เพื่อให้ตัวอักษรในวงกลมอ่านง่าย
        int i = 0;
        while (i < n.length() - 1 && "เแโใไ".indexOf(n.charAt(i)) >= 0) {
            i++;
        }
        return n.isEmpty() ? "?" : n.substring(i, n.offsetByCodePoints(i, 1));
    }

    /** คลาส CSS ของรูปโปรไฟล์ตามภาวะโภชนาการ */
    public String getAvatarClass() {
        if (status == null) {
            return "";
        }
        return switch (status.status()) {
            case SEVERE_THINNESS, THINNESS -> "blue";
            case NORMAL -> "good";
            case OVERWEIGHT -> "warn";
            case OBESITY -> "bad";
        };
    }

    /** ตำแหน่ง (0-100%) ของ z-score บนมาตรวัด -3.5 ถึง +3.5 */
    public double getZPercent() {
        if (status == null) {
            return 50;
        }
        double z = Math.max(-3.5, Math.min(3.5, status.z()));
        return (z + 3.5) / 7.0 * 100.0;
    }

    public String getAgeText() {
        return (ageMonths / 12) + " ปี " + (ageMonths % 12) + " เดือน";
    }

    /** กลุ่มเป้าหมายของโครงงานคือเด็กอายุ 6-12 ปี */
    public boolean isInTargetAge() {
        int years = ageMonths / 12;
        return years >= 6 && years <= 12;
    }
}
