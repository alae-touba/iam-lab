package com.alae.iam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class TestController {

    @GetMapping("/public")
    public String publicEndpoint() { return "Public endpoint."; }

    @GetMapping("/api/reader")
    @PreAuthorize("hasRole('API-reader')")
    public String readerEndpoint(@AuthenticationPrincipal Jwt jwt) {
        return "Access Granted: READER for " + jwt.getSubject();
    }

    @GetMapping("/api/writer")
    @PreAuthorize("hasRole('API-writer')")
    public String writerEndpoint(@AuthenticationPrincipal Jwt jwt) {
        return "Access Granted: WRITER for " + jwt.getSubject();
    }

    @GetMapping("/api/profile")
    @PreAuthorize("hasRole('API-reader')")
    public Map<String, Object> getProfile(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
            "username", jwt.getClaimAsString("preferred_username"),
            "department", jwt.getClaimAsString("department") != null ? jwt.getClaimAsString("department") : "Unknown",
            "audience", jwt.getAudience()
        );
    }
}
