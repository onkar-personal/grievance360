package com.grievance360.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/** Abstraction over binary attachment storage. A cloud object-store implementation can replace the local one. */
public interface StorageService {
    /** Stores the stream under a newly generated key and returns that key. */
    String store(InputStream data, String extension);

    Resource load(String key);

    void delete(String key);
}
