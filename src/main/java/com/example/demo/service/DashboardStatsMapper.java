package com.example.demo.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DashboardStatsMapper {
    private DashboardStatsMapper() {}

    static <E extends Enum<E>> Map<String, Long> completeCounts(Class<E> statusType, List<Object[]> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (E status : statusType.getEnumConstants()) {
            counts.put(status.name(), 0L);
        }
        for (Object[] row : rows) {
            counts.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    static double percentage(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : numerator * 100.0 / denominator;
    }

    static Map<String, Long> countMap(List<Object[]> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            counts.put(row[0] == null ? "Unassigned" : row[0].toString(), ((Number) row[1]).longValue());
        }
        return counts;
    }
}
