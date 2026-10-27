package com.grievance360.complaint;

import com.grievance360.common.PageResponse;
import com.grievance360.common.Paging;
import com.grievance360.complaint.ComplaintDtos.*;
import com.grievance360.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
@Tag(name = "Complaints")
public class ComplaintController {
    static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "dueAt", "reference", "status");

    private final ComplaintService service;
    private final AttachmentService attachmentService;

    @GetMapping
    @Operation(summary = "List complaints visible to the caller (citizen: own; officer: department/assigned; admin: all)")
    public PageResponse<ComplaintSummary> list(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) List<ComplaintStatus> status,
                                               @RequestParam(required = false) Priority priority,
                                               @RequestParam(required = false) Long departmentId,
                                               @RequestParam(required = false) Long categoryId,
                                               @RequestParam(required = false) Long officerId,
                                               @RequestParam(required = false) Boolean unassigned,
                                               @RequestParam(required = false) Boolean mine,
                                               @RequestParam(required = false) Boolean overdue,
                                               @RequestParam(required = false) Boolean escalated,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @RequestParam(required = false) String sort) {
        var filter = new ComplaintFilter(q, status, priority, departmentId, categoryId, officerId, unassigned, mine,
                overdue, escalated, from, to);
        return service.list(filter, Paging.of(page, size, sort, SORTABLE, "createdAt,desc"), CurrentUser.get());
    }

    @PostMapping
    @PreAuthorize("hasRole('CITIZEN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a new complaint (citizen)")
    public ComplaintDetail create(@Valid @RequestBody CreateComplaintRequest req) {
        return service.create(req, CurrentUser.get());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Complaint details")
    public ComplaintDetail get(@PathVariable Long id) {
        return service.get(id, CurrentUser.get());
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Immutable status history")
    public List<HistoryEntry> history(@PathVariable Long id) {
        return service.history(id, CurrentUser.get());
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "Comments (citizens see public remarks only)")
    public List<CommentDto> comments(@PathVariable Long id) {
        return service.comments(id, CurrentUser.get());
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a follow-up message, remark or internal note")
    public CommentDto addComment(@PathVariable Long id, @Valid @RequestBody CommentRequest req) {
        return service.addComment(id, req, CurrentUser.get());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OFFICER','ADMIN')")
    @Operation(summary = "Change status following the workflow rules")
    public ComplaintDetail updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest req) {
        return service.updateStatus(id, req, CurrentUser.get());
    }

    @PostMapping("/{id}/assignment")
    @PreAuthorize("hasAnyRole('OFFICER','ADMIN')")
    @Operation(summary = "Assign or reassign (admin) or claim (officer, self only)")
    public ComplaintDetail assign(@PathVariable Long id, @Valid @RequestBody AssignRequest req) {
        return service.assign(id, req, CurrentUser.get());
    }

    @PatchMapping("/{id}/priority")
    @PreAuthorize("hasAnyRole('OFFICER','ADMIN')")
    @Operation(summary = "Change priority (recomputes SLA due time)")
    public ComplaintDetail priority(@PathVariable Long id, @Valid @RequestBody PriorityRequest req) {
        return service.changePriority(id, req, CurrentUser.get());
    }

    @PostMapping("/{id}/reroute")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Move a complaint to another category/department")
    public ComplaintDetail reroute(@PathVariable Long id, @Valid @RequestBody RerouteRequest req) {
        return service.reroute(id, req, CurrentUser.get());
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('CITIZEN','ADMIN')")
    @Operation(summary = "Reopen a resolved complaint with a reason")
    public ComplaintDetail reopen(@PathVariable Long id, @Valid @RequestBody ReopenRequest req) {
        return service.reopen(id, req, CurrentUser.get());
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Confirm the resolution and close the complaint")
    public ComplaintDetail close(@PathVariable Long id, @Valid @RequestBody(required = false) CloseRequest req) {
        return service.confirmClose(id, req, CurrentUser.get());
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload evidence (JPEG/PNG/WEBP/PDF, max 5 MB each)")
    public AttachmentDto upload(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return attachmentService.upload(id, file, CurrentUser.get());
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    @Operation(summary = "Download an attachment")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long id, @PathVariable Long attachmentId) {
        var d = attachmentService.download(id, attachmentId, CurrentUser.get());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.contentType()))
                .contentLength(d.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(d.resource());
    }
}
