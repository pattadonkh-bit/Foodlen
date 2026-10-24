package com.example.demo.service;

import java.net.http.HttpClient;
import java.time.Duration;

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

    private final RestClient restClient;
    private final RestClient healthClient;

    public YoloService(@Value("${foodlen.ai-service-url:http://127.0.0.1:5000}") String baseUrl,
                       @Value("${foodlen.ai-token:}") String token,
                       @Value("${foodlen.ai-read-timeout-seconds:30}") int readTimeoutSeconds) {
        this.restClient = build(baseUrl, token, Duration.ofSeconds(10), Duration.ofSeconds(readTimeoutSeconds));
        // ตรวจสถานะสั้นๆ ไม่ให้หน้าแรกค้างเมื่อบริการ AI กำลังหลับ (คำขอนี้ยังช่วยปลุกบริการให้ตื่น)
        this.healthClient = build(baseUrl, token, Duration.ofSeconds(2), Duration.ofSeconds(3));
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

    public AiResult predict(MultipartFile image) throws AiServiceException {
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
            throw new AiServiceException("เชื่อมต่อหรือเรียกบริการ AI ไม่สำเร็จ (ถ้าเพิ่งเปิดเว็บ บริการ AI อาจกำลังตื่นจากการหลับ ลองใหม่ใน 1 นาที)", e);
        } catch (java.io.IOException e) {
            throw new AiServiceException("อ่านไฟล์ภาพไม่ได้", e);
        }
    }

    public boolean isUp() {
        try {
            healthClient.get().uri("/health").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }
}
