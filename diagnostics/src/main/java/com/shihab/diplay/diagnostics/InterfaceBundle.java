package com.shihab.diplay.diagnostics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Copies explicitly selected firmware code, never application data. No network or shell. */
final class InterfaceBundle {
    static final long FILE_LIMIT = 96L * 1024 * 1024;
    static final long TOTAL_LIMIT = 192L * 1024 * 1024;

    static final class Source {
        final File file;
        final String entry;
        Source(File file, String entry) { this.file = file; this.entry = entry; }
    }

    static File write(File directory, String report, List<Source> sources) throws IOException {
        return write(directory, report, sources, FILE_LIMIT, TOTAL_LIMIT);
    }

    static File write(File directory, String report, List<Source> sources,
                      long fileLimit, long totalLimit) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create bundle directory");
        File partial = File.createTempFile("E01-bluetooth-", ".partial", directory);
        File result = new File(directory, partial.getName().replace(".partial", ".zip"));
        boolean committed = false;
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(partial))) {
                text(zip, "report.txt", report);
                StringBuilder index = new StringBuilder("status\tbytes\tsha256\tentry\n");
                Set<String> names = new HashSet<>();
                long total = 0;
                for (Source source : sources) {
                    cancelled();
                    validateEntry(source.entry);
                    if (!names.add(source.entry)) continue;
                    long length = source.file.length();
                    String skip = !source.file.isFile() || !source.file.canRead() ? "unreadable"
                        : length <= 0 ? "empty" : length > fileLimit || length > totalLimit - total ? "size_limit" : null;
                    if (skip != null) {
                        index.append(skip).append('\t').append(length).append("\t-\t").append(source.entry).append('\n');
                        continue;
                    }
                    MessageDigest digest = sha256();
                    zip.putNextEntry(new ZipEntry(source.entry));
                    long copied = 0;
                    try (FileInputStream input = new FileInputStream(source.file)) {
                        byte[] buffer = new byte[32768];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            cancelled();
                            copied += count;
                            if (copied > fileLimit || copied > totalLimit - total) throw new IOException("Firmware file grew beyond limit");
                            zip.write(buffer, 0, count);
                            digest.update(buffer, 0, count);
                        }
                    }
                    if (copied != length) throw new IOException("Firmware file changed during export");
                    zip.closeEntry();
                    total += copied;
                    index.append("included\t").append(copied).append('\t').append(hex(digest.digest()))
                        .append('\t').append(source.entry).append('\n');
                }
                text(zip, "files.tsv", index.toString());
                cancelled();
            }
            if (!partial.renameTo(result)) throw new IOException("Cannot finish bundle");
            committed = true;
            return result;
        } finally {
            if (!committed) partial.delete();
        }
    }

    static void validateEntry(String entry) throws IOException {
        if (!entry.startsWith("firmware/") || entry.contains("\\") || entry.contains(":")) throw new IOException("Invalid archive path");
        for (String part : entry.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IOException("Invalid archive path");
            }
            for (int i = 0; i < part.length(); i++) if (part.charAt(i) < 32) throw new IOException("Invalid archive path");
        }
    }

    private static void cancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Export cancelled");
    }

    private static void text(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return out.toString();
    }
}
