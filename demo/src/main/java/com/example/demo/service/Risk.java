package com.example.demo.service;

/** ระดับความเสี่ยงรวมของเด็กในหน้าภาพรวมสำหรับครู/นักโภชนาการ (เรียงตามความเร่งด่วน) */
public enum Risk {
    HIGH("เสี่ยงสูง", "bad", 3),
    MEDIUM("เฝ้าระวัง", "warn", 2),
    LOW("ปกติ", "good", 1),
    UNKNOWN("ประเมินไม่ได้", "plain", 0);

    private final String label;
    private final String cssClass;
    private final int rank;

    Risk(String label, String cssClass, int rank) {
        this.label = label;
        this.cssClass = cssClass;
        this.rank = rank;
    }

    public String getLabel() {
        return label;
    }

    public String getCssClass() {
        return cssClass;
    }

    public int getRank() {
        return rank;
    }

    public static Risk worst(Risk a, Risk b) {
        return a.rank >= b.rank ? a : b;
    }
}
