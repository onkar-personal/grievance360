package com.grievance360.complaint;

import com.grievance360.common.ApiException;
import com.grievance360.config.AppProperties;
import com.grievance360.security.RateLimiter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnitHelpersTest {

    @Test
    void referenceFormatIsZeroPadded() {
        assertThat(ReferenceGenerator.format(2026, 42)).isEqualTo("G360-2026-000042");
        assertThat(ReferenceGenerator.format(2026, 1234567)).isEqualTo("G360-2026-1234567");
    }

    @Test
    void fileTypeIsDetectedFromMagicBytes() {
        assertThat(AttachmentService.detectType(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEqualTo("image/jpeg");
        assertThat(AttachmentService.detectType(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})).isEqualTo("image/png");
        assertThat(AttachmentService.detectType("%PDF-1.7".getBytes())).isEqualTo("application/pdf");
        assertThat(AttachmentService.detectType("MZ\u0090\u0000".getBytes())).isNull();
        assertThat(AttachmentService.detectType("<html>".getBytes())).isNull();
    }

    @Test
    void uploadedNamesAreSanitised() {
        assertThat(AttachmentService.sanitizeName("../../etc/passwd.png", "png")).isEqualTo(".._.._etc_passwd.png");
        assertThat(AttachmentService.sanitizeName("photo<script>.exe", "jpg")).isEqualTo("photo_script_.jpg");
        assertThat(AttachmentService.sanitizeName(null, "pdf")).isEqualTo("attachment.pdf");
    }

    @Test
    void rateLimiterBlocksAfterLimit() {
        var props = new AppProperties(null, null, null, new AppProperties.Auth(3, 60), null, null);
        RateLimiter limiter = new RateLimiter(props);
        limiter.check("k");
        limiter.check("k");
        limiter.check("k");
        assertThatThrownBy(() -> limiter.check("k")).isInstanceOf(ApiException.class)
                .hasMessageContaining("Too many attempts");
        limiter.check("other");
    }
}
