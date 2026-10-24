package com.example.demo.nutrition;

/**
 * กลุ่มภาวะโภชนาการตาม WHO Growth Reference 2007 (BMI-for-age, 5-19 ปี)
 * ชื่อภาษาไทยเป็นการแปลโดยประมาณ ต้องตรวจกับเกณฑ์ที่ใช้อ้างอิงจริง (เช่น เกณฑ์ของกรมอนามัย)
 */
public enum StatusCode {
    SEVERE_THINNESS("ผอมมาก", "UNDERWEIGHT", "bad"),
    THINNESS("ผอม", "UNDERWEIGHT", "warn"),
    NORMAL("สมส่วน", "NORMAL", "good"),
    OVERWEIGHT("น้ำหนักเกิน/ท้วม", "OVERWEIGHT", "warn"),
    OBESITY("อ้วน", "OVERWEIGHT", "bad");

    private final String label;
    private final String group;
    private final String cssClass;

    StatusCode(String label, String group, String cssClass) {
        this.label = label;
        this.group = group;
        this.cssClass = cssClass;
    }

    public String getLabel() {
        return label;
    }

    /** UNDERWEIGHT / NORMAL / OVERWEIGHT (ใช้จับคู่กับกฎคำแนะนำ) */
    public String getGroup() {
        return group;
    }

    public String getCssClass() {
        return cssClass;
    }

    public static StatusCode fromZ(double z) {
        if (z < -3) {
            return SEVERE_THINNESS;
        }
        if (z < -2) {
            return THINNESS;
        }
        if (z <= 1) {
            return NORMAL;
        }
        if (z <= 2) {
            return OVERWEIGHT;
        }
        return OBESITY;
    }
}
