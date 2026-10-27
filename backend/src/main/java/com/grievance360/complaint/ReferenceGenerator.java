package com.grievance360.complaint;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Year;

/** Server-generated, human-readable complaint references such as G360-2026-000042, backed by a DB sequence. */
@Component
@RequiredArgsConstructor
public class ReferenceGenerator {
    private final ComplaintRepository complaints;
    private final Clock clock = Clock.systemUTC();

    public String next() {
        long n = complaints.nextReferenceNumber();
        return format(Year.now(clock).getValue(), n);
    }

    static String format(int year, long n) {
        return "G360-%d-%06d".formatted(year, n);
    }
}
