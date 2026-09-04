package com.stocksense.stocksense_backend.controllers;

import com.stocksense.stocksense_backend.dtos.DashboardOverviewDTO;
import com.stocksense.stocksense_backend.services.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@CrossOrigin("*")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public ResponseEntity<DashboardOverviewDTO> getDashboardOverview(@org.springframework.security.core.annotation.AuthenticationPrincipal String userEmail) {
        if (userEmail == null) {
            return ResponseEntity.status(401).build();
        }
        DashboardOverviewDTO overview = dashboardService.getDashboardOverview(userEmail);
        return ResponseEntity.ok(overview);
    }
}
