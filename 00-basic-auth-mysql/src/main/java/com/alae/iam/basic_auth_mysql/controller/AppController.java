package com.alae.iam.basic_auth_mysql.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alae.iam.basic_auth_mysql.service.AppService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AppController {

  private final AppService appService;

  @GetMapping("/public")
  public String publicEndpoint() {
    return "This is public";
  }

  @GetMapping("/home")
  public String home(Authentication auth) {
    return appService.getHomePage(auth);
  }
}
