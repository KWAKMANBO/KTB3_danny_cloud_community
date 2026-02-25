package com.ktb.community.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/auth/consent")
    public String getConsentPage() {
        return "index";
    }
}