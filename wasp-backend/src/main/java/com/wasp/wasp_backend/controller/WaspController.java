package com.wasp.wasp_backend.controller;

import com.wasp.wasp_backend.repository.MetricRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WaspController {

  private final MetricRepository repo;

  public WaspController(MetricRepository repo){
    this.repo = repo;
  }

  @GetMapping("/health")
  public String health(){
    return "Backend is healthy!";
  }
}
