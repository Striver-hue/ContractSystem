package com.example.contractsystem.controller;

import com.example.contractsystem.service.SlitherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/slither")
public class SlitherController {

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestParam("file") MultipartFile file) {
        try {
            String result = SlitherService.runSlither(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}