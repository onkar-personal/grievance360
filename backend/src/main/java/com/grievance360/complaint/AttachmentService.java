package com.grievance360.complaint;

import com.grievance360.common.ApiException;
import com.grievance360.complaint.ComplaintDtos.AttachmentDto;
import com.grievance360.config.AppProperties;
import com.grievance360.security.UserPrincipal;
import com.grievance360.storage.StorageService;
import com.grievance360.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class AttachmentService {
    private final ComplaintService complaintService;
    private final ComplaintAccessGuard guard;
    private final AttachmentRepository attachments;
    private final UserRepository users;
    private final StorageService storage;
    private final AppProperties props;

    public record Download(Resource resource, String filename, String contentType, long size) {}

    @Transactional
    public AttachmentDto upload(Long complaintId, MultipartFile file, UserPrincipal p) {
        Complaint c = complaintService.loadVisible(complaintId, p);
        boolean allowed = guard.isOwner(p, c) || guard.canManage(p, c);
        if (!allowed) throw ApiException.forbidden("You cannot add files to this complaint");
        if (c.getStatus() == ComplaintStatus.CLOSED || c.getStatus() == ComplaintStatus.REJECTED) {
            throw ApiException.conflict("COMPLAINT_FINALISED", "Files cannot be added to a closed complaint");
        }
        if (file == null || file.isEmpty()) throw ApiException.badRequest("EMPTY_FILE", "File is empty");
        if (file.getSize() > props.storage().maxFileBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "Each file must be at most " + props.storage().maxFileBytes() / (1024 * 1024) + " MB");
        }
        if (attachments.countByComplaintId(c.getId()) >= props.storage().maxFilesPerComplaint()) {
            throw ApiException.conflict("TOO_MANY_FILES", "A complaint can have at most "
                    + props.storage().maxFilesPerComplaint() + " attachments");
        }

        // Trust the file's bytes, not the client-declared content type or extension.
        String detected;
        try (InputStream in = file.getInputStream()) {
            detected = detectType(in.readNBytes(12));
        } catch (IOException e) {
            throw ApiException.badRequest("UNREADABLE_FILE", "File could not be read");
        }
        if (detected == null || !props.storage().allowedTypeList().contains(detected)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
                    "Only JPEG, PNG, WEBP images and PDF documents are allowed");
        }

        String key;
        try (InputStream in = file.getInputStream()) {
            key = storage.store(in, extensionFor(detected));
        } catch (IOException e) {
            throw ApiException.badRequest("UNREADABLE_FILE", "File could not be read");
        }
        // If the transaction rolls back, remove the orphaned file.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            final String k = key;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) storage.delete(k);
                }
            });
        }

        Attachment a = new Attachment();
        a.setComplaint(c);
        a.setUploadedBy(users.getReferenceById(p.id()));
        a.setOriginalName(sanitizeName(file.getOriginalFilename(), extensionFor(detected)));
        a.setContentType(detected);
        a.setSizeBytes(file.getSize());
        a.setStorageKey(key);
        attachments.save(a);
        return ComplaintMapper.attachment(a);
    }

    @Transactional(readOnly = true)
    public Download download(Long complaintId, Long attachmentId, UserPrincipal p) {
        complaintService.loadVisible(complaintId, p);
        Attachment a = attachments.findByIdAndComplaintId(attachmentId, complaintId)
                .orElseThrow(() -> ApiException.notFound("Attachment"));
        return new Download(storage.load(a.getStorageKey()), a.getOriginalName(), a.getContentType(), a.getSizeBytes());
    }

    static String detectType(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (h.length >= 8 && Arrays.equals(Arrays.copyOf(h, 8), new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) return "image/png";
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "image/webp";
        if (h.length >= 5 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F' && h[4] == '-') return "application/pdf";
        return null;
    }

    private static String extensionFor(String type) {
        return switch (type) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            default -> "bin";
        };
    }

    static String sanitizeName(String original, String ext) {
        String base = original == null ? "attachment" : original.replaceAll("[\\\\/]", "_");
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        base = base.replaceAll("[^A-Za-z0-9 ._()-]", "_").trim();
        if (base.isEmpty()) base = "attachment";
        if (base.length() > 100) base = base.substring(0, 100);
        return base + "." + ext;
    }
}
