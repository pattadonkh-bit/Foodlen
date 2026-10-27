package com.example.demo.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

/**
 * ตัวเรียกบริการ AI (Python, โฟลเดอร์ yolo-service) เพื่อจำแนกอาหารไทยจากภาพ
 * (ชื่อคลาสเดิมจากเดโมแรก คงไว้เพื่อไม่ให้โครงสร้างโปรเจกต์เปลี่ยนมาก)
 *
 * <p>ถ้าตั้ง foodlen.ai-token บริการ AI จะรับเฉพาะคำขอที่ส่ง header X-Foodlen-Token ตรงกัน (ใช้เมื่อบริการ AI เปิดเป็น URL สาธารณะ เช่น Render free)
 * foodlen.ai-read-timeout-seconds ควรเพิ่มเป็น ~90 เมื่อบริการ AI อยู่บนแพ็กเกจที่หลับเมื่อว่าง (ปลุกครั้งแรกใช้เวลานาน)
 */
@Service
public class YoloService {

    static final String TOKEN_HEADER = "X-Foodlen-Token";

    private static final Logger log = LoggerFactory.getLogger(YoloService.class);

    private final RestClient restClient;
    private final RestClient healthClient;
    private final int wakeMaxSeconds;

    public YoloService(@Value("${foodlen.ai-service-url:http://127.0.0.1:5000}") String baseUrl,
                       @Value("${foodlen.ai-token:}") String token,
                       @Value("${foodlen.ai-read-timeout-seconds:30}") int readTimeoutSeconds,
                       @Value("${foodlen.ai-wake-max-seconds:60}") int wakeMaxSeconds) {
        this.restClient = build(baseUrl, token, Duration.ofSeconds(10), Duration.ofSeconds(readTimeoutSeconds));
        // ตรวจสถานะสั้นๆ ไม่ให้หน้าแรกค้างเมื่อบริการ AI กำลังหลับ (คำขอนี้ยังช่วยปลุกบริการให้ตื่น)
        this.healthClient = build(baseUrl, token, Duration.ofSeconds(2), Duration.ofSeconds(3));
        this.wakeMaxSeconds = wakeMaxSeconds;
    }

    private static RestClient build(String baseUrl, String token, Duration connect, Duration read) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(connect).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(read);
        RestClient.Builder b = RestClient.builder().baseUrl(baseUrl).requestFactory(factory);
        if (token != null && !token.isBlank()) {
            b.defaultHeader(TOKEN_HEADER, token.trim());
        }
        return b.build();
    }

    /**
     * จำแนกภาพ ถ้าบริการ AI กำลังหลับ (แพ็กเกจฟรีหยุดพักเมื่อไม่มีผู้ใช้) จะปลุกและรอให้ตื่นก่อน (ไม่เกิน foodlen.ai-wake-max-seconds)
     * แล้วจึงส่งภาพ ถ้าเรียกไม่สำเร็จจะลองใหม่อีกครั้ง และบันทึกสาเหตุจริงลง log
     */
    public AiResult predict(MultipartFile image) throws AiServiceException {
        if (!isUp()) {
            log.info("บริการ AI ยังไม่ตอบ /health จึงรอให้ตื่นก่อนส่งภาพ (สูงสุด {} วินาที)", wakeMaxSeconds);
            waitUntilUp();
        }
        try {
            return send(image);
        } catch (AiServiceException first) {
            if (first.getCause() == null) {
                throw first; // ไม่ใช่ปัญหาการเชื่อมต่อ (เช่น AI ตอบแต่ไม่มีผลจำแนก) ไม่ต้องลองใหม่
            }
            log.warn("เรียกบริการ AI ครั้งแรกไม่สำเร็จ: {} จะรอให้ตื่นแล้วลองอีกครั้ง", first.getCause().toString());
            waitUntilUp();
            return send(image);
        }
    }

    /** เรียกให้ AI เริ่มตื่นล่วงหน้า (ไม่รอผล) ใช้ตอนเปิดหน้าอัปโหลดภาพ เพื่อให้พร้อมก่อนผู้ใช้กดวิเคราะห์ */
    public void wakeAsync() {
        CompletableFuture.runAsync(this::isUp);
    }

    private void waitUntilUp() {
        long deadline = System.nanoTime() + Duration.ofSeconds(wakeMaxSeconds).toNanos();
        while (System.nanoTime() < deadline) {
            if (isUp()) {
                return;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("รอบริการ AI ตื่นเกิน {} วินาทีแล้วยังไม่ตอบ", wakeMaxSeconds);
    }

    private AiResult send(MultipartFile image) throws AiServiceException {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (image.getContentType() != null && image.getContentType().startsWith("image/")) {
                mediaType = MediaType.parseMediaType(image.getContentType());
            }
            final String filename = image.getOriginalFilename() == null ? "image.jpg" : image.getOriginalFilename();
            builder.part("image", new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            }).contentType(mediaType);

            AiResult result = restClient.post()
                    .uri("/predict")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(AiResult.class);
            if (result == null || result.predictions() == null || result.predictions().isEmpty()) {
                throw new AiServiceException("บริการ AI ไม่ส่งผลการจำแนกกลับมา");
            }
            return result;
        } catch (RestClientException e) {
            log.error("เรียกบริการ AI ไม่สำเร็จ", e);
            throw new AiServiceException("เชื่อมต่อหรือเรียกบริการ AI ไม่สำเร็จ (ถ้าเพิ่งเปิดเว็บ บริการ AI อาจกำลังตื่นจากการหลับ ลองใหม่ใน 1 นาที)", e);
        } catch (java.io.IOException e) {
            throw new AiServiceException("อ่านไฟล์ภาพไม่ได้", e);
        }
    }

    /**
     * บริการ AI พร้อมใช้งานหรือไม่ ต้องได้ JSON {"status":"ok"} จริง ไม่นับหน้า "กำลังปลุกบริการ" (HTML) ที่ Render ตอบแทน
     * ระหว่างที่บริการแพ็กเกจฟรีกำลังตื่น
     */
    public boolean isUp() {
        try {
            String body = healthClient.get().uri("/health").retrieve().body(String.class);
            return body != null && body.contains("\"status\"") && body.contains("\"ok\"");
        } catch (RestClientException e) {
            return false;
        }
    }
}
