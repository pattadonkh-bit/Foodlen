package com.example.demo.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.advice.EnergyTargetService;
import com.example.demo.domain.AppUserRepository;
import com.example.demo.domain.Child;
import com.example.demo.domain.ChildRepository;
import com.example.demo.domain.MealRepository;
import com.example.demo.domain.Measurement;
import com.example.demo.domain.MeasurementRepository;
import com.example.demo.domain.Role;
import com.example.demo.nutrition.NutritionStatusService;
import com.example.demo.nutrition.Sex;
import com.example.demo.nutrition.StatusResult;
import com.example.demo.nutrition.UnsupportedAgeException;
import com.example.demo.security.AppUserPrincipal;
import com.example.demo.security.CurrentUser;

/**
 * จัดการข้อมูลเด็กและตรวจสิทธิ์: ครู/นักโภชนาการเห็นเด็กทุกคน ผู้ปกครองเห็นเฉพาะเด็กที่ตัวเองลงทะเบียน
 * เมธอดอื่น (มื้ออาหาร สรุปรายสัปดาห์ กราฟ) เรียก {@link #get(Long)} จึงผ่านการตรวจสิทธิ์เดียวกันทั้งหมด
 */
@Service
@Transactional
public class ChildService {

    private final ChildRepository children;
    private final MeasurementRepository measurements;
    private final MealRepository meals;
    private final AppUserRepository users;
    private final NutritionStatusService statusService;
    private final EnergyTargetService energyTargets;

    public ChildService(ChildRepository children, MeasurementRepository measurements, MealRepository meals,
                        AppUserRepository users, NutritionStatusService statusService,
                        EnergyTargetService energyTargets) {
        this.children = children;
        this.measurements = measurements;
        this.meals = meals;
        this.users = users;
        this.statusService = statusService;
        this.energyTargets = energyTargets;
    }

    /** เด็กที่ผู้ใช้ปัจจุบันมีสิทธิ์เห็น (ยังไม่ล็อกอิน = ไม่เห็นใคร) */
    @Transactional(readOnly = true)
    public List<ChildOverview> listOverviews() {
        AppUserPrincipal me = CurrentUser.get();
        if (me == null) {
            return List.of();
        }
        List<Child> list = me.role() == Role.STAFF
                ? children.findAllByOrderByNameAsc()
                : children.findByOwner_IdOrderByNameAsc(me.id());
        return list.stream().map(this::overview).toList();
    }

    /** ดึงเด็กตามรหัส ถ้าไม่มีสิทธิ์จะตอบเหมือนไม่พบ (ไม่เปิดเผยว่ามีเด็กรหัสนี้อยู่) */
    @Transactional(readOnly = true)
    public Child get(Long id) {
        Child c = children.findById(id).orElseThrow(() -> new ChildNotFoundException(id));
        if (!canAccess(c)) {
            throw new ChildNotFoundException(id);
        }
        return c;
    }

    @Transactional(readOnly = true)
    public ChildOverview overview(Long id) {
        return overview(get(id));
    }

    @Transactional(readOnly = true)
    public List<Measurement> history(Child child) {
        return measurements.findByChildOrderByMeasuredOnDescIdDesc(child);
    }

    @Transactional(readOnly = true)
    public Measurement getMeasurement(Long childId, Long measurementId) {
        Child c = get(childId);
        Measurement m = measurements.findById(measurementId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบข้อมูลการวัดนี้"));
        if (!m.getChild().getId().equals(c.getId())) {
            throw new IllegalArgumentException("ข้อมูลการวัดนี้ไม่ใช่ของเด็กคนนี้");
        }
        return m;
    }

    public Child create(Long ownerId, String name, Sex sex, LocalDate birthDate, String classroom, boolean consent,
                        LocalDate measuredOn, double weightKg, double heightCm) {
        validateProfile(name, sex, birthDate);
        if (!consent) {
            throw new IllegalArgumentException("ต้องได้รับความยินยอมจากผู้ปกครองก่อนบันทึกข้อมูลเด็ก");
        }
        validateMeasurement(sex, birthDate, measuredOn, weightKg, heightCm);

        Child c = new Child();
        c.setName(name.trim());
        c.setSex(sex);
        c.setBirthDate(birthDate);
        c.setClassroom(normalizeClassroom(classroom));
        c.setGuardianConsent(true);
        c.setConsentDate(LocalDate.now());
        c.setOwner(ownerId == null ? null : users.getReferenceById(ownerId));
        children.save(c);
        measurements.save(new Measurement(c, measuredOn, weightKg, heightCm));
        return c;
    }

    /** แก้ไขข้อมูลเด็ก ถ้าวันเกิด/เพศใหม่ทำให้การวัดเดิมใช้ไม่ได้ จะไม่บันทึกและบอกว่าการวัดครั้งไหน */
    public Child update(Long id, String name, Sex sex, LocalDate birthDate, String classroom) {
        Child c = get(id);
        validateProfile(name, sex, birthDate);
        for (Measurement m : measurements.findByChildOrderByMeasuredOnDescIdDesc(c)) {
            try {
                validateMeasurement(sex, birthDate, m.getMeasuredOn(), m.getWeightKg(), m.getHeightCm());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("แก้ไขไม่ได้ เพราะการวัดเมื่อ " + m.getMeasuredOn() + " จะไม่ถูกต้อง ("
                        + e.getMessage() + ") กรุณาแก้ไขหรือลบการวัดนั้นก่อน");
            }
        }
        c.setName(name.trim());
        c.setSex(sex);
        c.setBirthDate(birthDate);
        c.setClassroom(normalizeClassroom(classroom));
        return c;
    }

    public Measurement addMeasurement(Long childId, LocalDate measuredOn, double weightKg, double heightCm) {
        Child c = get(childId);
        validateMeasurement(c.getSex(), c.getBirthDate(), measuredOn, weightKg, heightCm);
        return measurements.save(new Measurement(c, measuredOn, weightKg, heightCm));
    }

    public Measurement updateMeasurement(Long childId, Long measurementId, LocalDate measuredOn,
                                         double weightKg, double heightCm) {
        Measurement m = getMeasurement(childId, measurementId);
        Child c = m.getChild();
        validateMeasurement(c.getSex(), c.getBirthDate(), measuredOn, weightKg, heightCm);
        m.update(measuredOn, weightKg, heightCm);
        return m;
    }

    public void deleteMeasurement(Long childId, Long measurementId) {
        measurements.delete(getMeasurement(childId, measurementId));
    }

    public void delete(Long id) {
        Child c = get(id);
        meals.deleteByChild(c);
        measurements.deleteByChild(c);
        children.delete(c);
    }

    private boolean canAccess(Child c) {
        AppUserPrincipal me = CurrentUser.get();
        if (me == null) {
            return false;
        }
        if (me.role() == Role.STAFF) {
            return true;
        }
        return c.getOwner() != null && c.getOwner().getId().equals(me.id());
    }

    private static void validateProfile(String name, Sex sex, LocalDate birthDate) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("กรุณากรอกชื่อเด็ก (ใช้ชื่อเล่นหรือรหัสได้)");
        }
        if (name.trim().length() > 100) {
            throw new IllegalArgumentException("ชื่อยาวเกินไป (ไม่เกิน 100 ตัวอักษร)");
        }
        if (sex == null) {
            throw new IllegalArgumentException("กรุณาเลือกเพศ");
        }
        if (birthDate == null || birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("วันเกิดไม่ถูกต้อง");
        }
    }

    private static String normalizeClassroom(String classroom) {
        if (classroom == null || classroom.isBlank()) {
            return null;
        }
        String s = classroom.trim();
        if (s.length() > 50) {
            throw new IllegalArgumentException("ชื่อชั้น/ห้องยาวเกินไป (ไม่เกิน 50 ตัวอักษร)");
        }
        return s;
    }

    private void validateMeasurement(Sex sex, LocalDate birth, LocalDate on, double weightKg, double heightCm) {
        if (on == null || on.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("วันที่วัดไม่ถูกต้อง");
        }
        if (on.isBefore(birth)) {
            throw new IllegalArgumentException("วันที่วัดต้องไม่ก่อนวันเกิด");
        }
        if (weightKg < 5 || weightKg > 150) {
            throw new IllegalArgumentException("น้ำหนักต้องอยู่ระหว่าง 5-150 กก.");
        }
        if (heightCm < 80 || heightCm > 220) {
            throw new IllegalArgumentException("ส่วนสูงต้องอยู่ระหว่าง 80-220 ซม.");
        }
        try {
            statusService.assess(sex, birth, on, weightKg, heightCm);
        } catch (UnsupportedAgeException e) {
            throw new IllegalArgumentException(
                    "ตาราง WHO ที่ใช้รองรับอายุ 5 ปี 1 เดือน ถึง 19 ปี (กลุ่มเป้าหมายของระบบคือ 6-12 ปี)");
        }
    }

    ChildOverview overview(Child c) {
        int ageMonthsNow = NutritionStatusService.ageInMonths(c.getBirthDate(), LocalDate.now());
        Measurement latest = measurements.findFirstByChildOrderByMeasuredOnDescIdDesc(c).orElse(null);
        StatusResult status = null;
        String problem = null;
        if (latest == null) {
            problem = "ยังไม่มีข้อมูลน้ำหนักและส่วนสูง";
        } else {
            try {
                status = statusService.assess(c.getSex(), c.getBirthDate(), latest.getMeasuredOn(),
                        latest.getWeightKg(), latest.getHeightCm());
            } catch (IllegalArgumentException e) {
                problem = e.getMessage();
            }
        }
        Integer daily = energyTargets.dailyKcal(c.getSex(), ageMonthsNow).orElse(null);
        return new ChildOverview(c, latest, status, problem, daily, ageMonthsNow);
    }
}
