package com.example.contractsystem.controller;

import com.example.contractsystem.repository.UserRepository;
import com.example.contractsystem.service.SlitherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/slither")
public class SlitherController {

    private final SlitherService slitherService;

    public SlitherController(SlitherService slitherService) {
        this.slitherService = slitherService;
    }

    @PostMapping("/analyze1")
    public ResponseEntity<?> analyze1(@RequestPart("file") MultipartFile file,
                                     @RequestPart("config") Map<String, Object> config) {
        try {
            String result = slitherService.runSlither1(file , config);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }
    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestPart("file") MultipartFile file) {
        try {
            String result = slitherService.runSlither(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }
}