package com.wasp.wasp_backend.controller;

import com.wasp.wasp_backend.repository.MetricRepository;
import com.wasp.wasp_backend.service.HistoryReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WaspController {

  private final MetricRepository repo;
  private final HistoryReportService historyReportService;

  public WaspController(MetricRepository repo, HistoryReportService historyReportService) {
    this.repo = repo;
    this.historyReportService = historyReportService;
  }

  @GetMapping("/health")
  public String health(){
    return "Backend is healthy!";
  }

  @GetMapping("/history-reports")
  public ResponseEntity<byte[]> downloadHistoryReport() {
    byte[] csv = historyReportService.generateHistoryCsv();

    return ResponseEntity.ok()
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=history-report.csv")
      .contentType(MediaType.parseMediaType("text/csv"))
      .body(csv);

  }
}
