package com.grievance360.complaint;

import com.grievance360.common.ApiException;
import com.grievance360.common.RequestContext;
import com.grievance360.complaint.ComplaintDtos.PublicStatus;
import com.grievance360.security.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public")
public class PublicController {
    private static final Pattern REF = Pattern.compile("^G360-\\d{4}-\\d{6}$", Pattern.CASE_INSENSITIVE);
    private final ComplaintService service;
    private final RateLimiter rateLimiter;

    @GetMapping("/complaints/{reference}")
    @Operation(summary = "Look up a complaint's status by reference. Returns no personal information.")
    public PublicStatus lookup(@PathVariable String reference) {
        rateLimiter.check("lookup:" + RequestContext.clientIp());
        if (!REF.matcher(reference.trim()).matches()) {
            throw ApiException.badRequest("INVALID_REFERENCE", "Reference must look like G360-2026-000123");
        }
        return service.publicLookup(reference);
    }
}
