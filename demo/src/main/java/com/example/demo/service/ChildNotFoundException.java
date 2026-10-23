package com.example.demo.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ChildNotFoundException extends RuntimeException {

    public ChildNotFoundException(Long id) {
        super("ไม่พบข้อมูลเด็กรหัส " + id);
    }
}
