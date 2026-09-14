package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.policy_service.dto.PurchaseInitiationRequest;
import com.claimassist.platform.policy_service.dto.PurchaseResponse;
import com.claimassist.platform.policy_service.dto.PurchaseStatusResponse;
import com.claimassist.platform.policy_service.service.PurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class PurchaseController {

    private final PurchaseService purchaseService;

    @PostMapping("/policies/purchase")
    public ResponseEntity<PurchaseResponse> purchase(
            @RequestBody @Valid PurchaseInitiationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        PurchaseResponse response = purchaseService.initiatePurchase(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/policies/purchases/{purchaseId}/status")
    public ResponseEntity<PurchaseStatusResponse> getPurchaseStatus(@PathVariable Long purchaseId) {
        return ResponseEntity.ok(purchaseService.getPurchaseStatus(purchaseId));
    }
}
