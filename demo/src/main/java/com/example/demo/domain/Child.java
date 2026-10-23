package com.example.demo.domain;

import java.time.LocalDate;

import com.example.demo.nutrition.Sex;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Child {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private Sex sex;

    @Column(nullable = false)
    private LocalDate birthDate;

    /** ผู้ปกครองยินยอมให้เก็บข้อมูลของเด็ก (PDPA) */
    @Column(nullable = false)
    private boolean guardianConsent;

    private LocalDate consentDate;

    /** ชั้น/ห้องเรียน (ไม่บังคับ) ใช้กรองในหน้าภาพรวมของครู */
    @Column(length = 50)
    private String classroom;

    /** ผู้ใช้ที่ลงทะเบียนเด็กคนนี้ (ผู้ปกครองเห็นเฉพาะเด็กของตัวเอง) ข้อมูลเก่าก่อนมีระบบล็อกอินเป็น null เห็นเฉพาะครู */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    public Long getId() {
        return id;
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

    public boolean isGuardianConsent() {
        return guardianConsent;
    }

    public void setGuardianConsent(boolean guardianConsent) {
        this.guardianConsent = guardianConsent;
    }

    public LocalDate getConsentDate() {
        return consentDate;
    }

    public void setConsentDate(LocalDate consentDate) {
        this.consentDate = consentDate;
    }

    public String getClassroom() {
        return classroom;
    }

    public void setClassroom(String classroom) {
        this.classroom = classroom;
    }

    public AppUser getOwner() {
        return owner;
    }

    public void setOwner(AppUser owner) {
        this.owner = owner;
    }
}
