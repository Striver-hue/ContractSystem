package com.example.contractsystem.controller;

import com.example.contractsystem.service.SlitherService;
import com.example.contractsystem.service.SmartAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/smartaudit")
public class SmartAuditController {
    private final SmartAuditService smartAuditService;

    public SmartAuditController(SmartAuditService smartAuditService) {
        this.smartAuditService = smartAuditService;
    }

    @PostMapping("/analyze1")
    public ResponseEntity<?> analyze1(@RequestPart("file") MultipartFile file,
                                     @RequestPart("config") Map<String, Object> config) {
        try {
            String result = smartAuditService.runSmartAudit1(file, config);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestPart("file") MultipartFile file) {
        try {
            String result = smartAuditService.runSmartAudit(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    @PostMapping("/getBAList")
    public ResponseEntity<?> getBAList() {
        try {
            String result = smartAuditService.getBAList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    @PostMapping("/getTAList")
    public ResponseEntity<?> getTAList() {
        try {
            String result = smartAuditService.getTAList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }
    @PostMapping("/getBAExample")
    public ResponseEntity<?> getBAExample(@RequestParam(value = "filename", defaultValue = "RealWord_20240812223706") String filename) {
        try {
            String result = smartAuditService.getBAExample(filename);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    @PostMapping("/getTAExample")
    public ResponseEntity<?> getTAExample(@RequestParam(value = "filename", defaultValue = "Labeled_20240813213630") String filename) {
        try {
            String result = smartAuditService.getTAExample(filename);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }
}