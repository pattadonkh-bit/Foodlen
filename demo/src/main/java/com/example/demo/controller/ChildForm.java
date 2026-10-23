package com.example.demo.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.example.demo.nutrition.Sex;

/** ข้อมูลจากฟอร์มลงทะเบียนเด็ก */
public class ChildForm {

    private String name;
    private Sex sex;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthDate;

    private boolean consent;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate measuredOn = LocalDate.now();

    private Double weightKg;
    private Double heightCm;

    /** ชั้น/ห้องเรียน (ไม่บังคับ) */
    private String classroom;

    public String getClassroom() {
        return classroom;
    }

    public void setClassroom(String classroom) {
        this.classroom = classroom;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Sex getSex() {
        return sex;
    }

    public void setSex(Sex sex) {
        this.sex = sex;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public boolean isConsent() {
        return consent;
    }

    public void setConsent(boolean consent) {
        this.consent = consent;
    }

    public LocalDate getMeasuredOn() {
        return measuredOn;
    }

    public void setMeasuredOn(LocalDate measuredOn) {
        this.measuredOn = measuredOn;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public Double getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(Double heightCm) {
        this.heightCm = heightCm;
    }
}
