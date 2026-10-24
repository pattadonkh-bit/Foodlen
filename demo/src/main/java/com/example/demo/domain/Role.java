package com.example.demo.domain;

/** บทบาทผู้ใช้: ผู้ปกครองเห็นเฉพาะเด็กของตัวเอง ครู/นักโภชนาการเห็นเด็กทุกคน */
public enum Role {
    PARENT("ผู้ปกครอง"),
    STAFF("ครู/นักโภชนาการ");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
