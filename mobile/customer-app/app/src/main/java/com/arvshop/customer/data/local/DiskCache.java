package com.arvshop.customer.data.local;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Tiny atomic file cache for the catalog JSON documents (offline-first, Phase 10).
 * Writes go to a temp file then rename, so a crash mid-write never corrupts the cache.
 */
public final class DiskCache {

    private final File dir;

    public DiskCache(File cacheRoot) {
        this.dir = new File(cacheRoot, "catalog");
        //noinspection ResultOfMethodCallIgnored
        this.dir.mkdirs();
    }

    public synchronized void put(String name, String content) throws IOException {
        File tmp = new File(dir, name + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        File dst = new File(dir, name);
        if (!tmp.renameTo(dst)) {
            throw new IOException("Cache rename failed for " + name);
        }
    }

    /** @return cached content, or null when absent/unreadable. */
    public synchronized String get(String name) {
        File f = new File(dir, name);
        if (!f.isFile()) return null;
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public synchronized boolean has(String name) {
        return new File(dir, name).isFile();
    }
}
