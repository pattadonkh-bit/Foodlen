package com.example.demo.domain;

/** วิธีที่ได้เมนูของมื้ออาหาร */
public enum MealSource {
    AI_AUTO("AI จำแนก (ความมั่นใจถึงเกณฑ์)"),
    AI_CONFIRMED("AI จำแนก และผู้ใช้ยืนยัน/แก้ไข"),
    MANUAL("ผู้ใช้เลือกเมนูเอง");

    private final String label;

    MealSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
