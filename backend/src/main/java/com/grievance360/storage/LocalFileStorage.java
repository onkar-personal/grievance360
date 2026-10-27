package com.grievance360.storage;

import com.grievance360.common.ApiException;
import com.grievance360.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LocalFileStorage implements StorageService {
    private static final Pattern KEY = Pattern.compile("^\\d{4}/\\d{2}/[0-9a-f\\-]{36}\\.[a-z]{2,5}$");
    private final Path root;

    public LocalFileStorage(AppProperties props) {
        this.root = Path.of(props.storage().dir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage directory " + root, e);
        }
    }

    @Override
    public String store(InputStream data, String extension) {
        LocalDate d = LocalDate.now();
        String key = "%04d/%02d/%s.%s".formatted(d.getYear(), d.getMonthValue(), UUID.randomUUID(), extension);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(data, target);
            return key;
        } catch (IOException e) {
            log.error("Failed to store attachment", e);
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Could not store file");
        }
    }

    @Override
    public Resource load(String key) {
        try {
            Resource r = new UrlResource(resolve(key).toUri());
            if (!r.exists()) throw ApiException.notFound("File");
            return r;
        } catch (MalformedURLException e) {
            throw ApiException.notFound("File");
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete stored file {}", key);
        }
    }

    private Path resolve(String key) {
        if (!KEY.matcher(key).matches()) throw ApiException.badRequest("INVALID_KEY", "Invalid storage key");
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw ApiException.badRequest("INVALID_KEY", "Invalid storage key");
        return p;
    }
}
