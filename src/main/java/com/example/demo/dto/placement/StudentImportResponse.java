package com.example.demo.dto.placement;

public record StudentImportResponse(int imported, int skipped, java.util.List<String> errors) {
}
