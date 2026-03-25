package com.example.contractsystem.controller;

import com.example.contractsystem.entity.User;
import com.example.contractsystem.repository.UserRepository;
import com.example.contractsystem.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/bac")
public class UserController {

    @Autowired
    private UserService userService;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public String register(@RequestParam String username, @RequestParam String password , @RequestParam(defaultValue = "USER") String role) {
        if (userRepository.findByUsername(username).isPresent()) {
            return "用户名已存在";
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        userRepository.save(user);
        return "注册成功";
    }

    @GetMapping("/only")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminAccess() {
        return "Hello Admin!";
    }

    @GetMapping("/any")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public String userAccess() {
        return "Hello User!";
    }

    @GetMapping("/vip")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_USER')")
    public String superUserAccess() {
        return "Hello VIP!";
    }


}