// Testing dynamic PR logiccc

package com.poc.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {

    // 🚨 HARDCODED SECRET FOR GITLEAKS & SONARQUBE ��
    private String stripeSecretKey = "sk_live_51J0XabcDEFghiJKLmnoPQRstuVWXyz1234567890";

    @GetMapping("/status")
    public String getStatus() {
        // 🚨 BAD PRACTICE FOR SONARQUBE 🚨
        System.out.println("API is running with key: " + stripeSecretKey);
        return "API is Up and Running!";
    }
}
