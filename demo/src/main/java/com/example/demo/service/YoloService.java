
package com.example.demo.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Service
public class YoloService {

    private final RestClient restClient = RestClient.create();

    public String detect(MultipartFile image) throws Exception {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;

        if (image.getContentType() != null
                && image.getContentType().startsWith("image/")) {
            mediaType = MediaType.parseMediaType(image.getContentType());
        }

        builder.part("image", new ByteArrayResource(image.getBytes()) {
            @Override
            public String getFilename() {
                return image.getOriginalFilename();
            }
        }).contentType(mediaType);

        return restClient.post()
                .uri("http://127.0.0.1:5000/detect")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(String.class);
    }
}
