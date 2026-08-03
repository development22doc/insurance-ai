package com.claimassist.platform.common_lib.dto;

public record ClaimDocumentSummaryDto(
        Long documentId,
        String docType,
        String ocrStatus,
        String extractedText,
        Double fraudSignalScore
) {}
