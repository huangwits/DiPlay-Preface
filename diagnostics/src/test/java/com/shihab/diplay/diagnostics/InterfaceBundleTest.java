package com.shihab.diplay.diagnostics;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

public class InterfaceBundleTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File source(String name, String content) throws IOException {
        File file = tmp.newFile(name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test public void archiveIncludesExactCodeBytesReportAndDigest() throws Exception {
        File original = source("Bluetooth.apk", "abc");
        File archive = InterfaceBundle.write(tmp.newFolder(), "firmware=E01\n", Collections.singletonList(
            new InterfaceBundle.Source(original, "firmware/system/app/Bluetooth.apk")));
        try (ZipFile zip = new ZipFile(archive)) {
            assertEquals("abc", read(zip, "firmware/system/app/Bluetooth.apk"));
            assertEquals("firmware=E01\n", read(zip, "report.txt"));
            assertTrue(read(zip, "files.tsv").contains("included\t3\tba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        }
        assertEquals("abc", new String(Files.readAllBytes(original.toPath()), StandardCharsets.UTF_8));
    }

    @Test public void sizeLimitsAndMissingFilesAreReportedInsteadOfTruncatedCode() throws Exception {
        File archive = InterfaceBundle.write(tmp.newFolder(), "report", Arrays.asList(
            new InterfaceBundle.Source(source("large.jar", "12345"), "firmware/system/large.jar"),
            new InterfaceBundle.Source(source("small.jar", "abc"), "firmware/system/small.jar"),
            new InterfaceBundle.Source(source("budget.jar", "abc"), "firmware/system/budget.jar"),
            new InterfaceBundle.Source(new File(tmp.getRoot(), "missing"), "firmware/system/missing.jar")
        ), 4, 4);
        try (ZipFile zip = new ZipFile(archive)) {
            assertNull(zip.getEntry("firmware/system/large.jar"));
            assertEquals("abc", read(zip, "firmware/system/small.jar"));
            assertNull(zip.getEntry("firmware/system/budget.jar"));
            String index = read(zip, "files.tsv");
            assertTrue(index.contains("size_limit\t5"));
            assertTrue(index.contains("size_limit\t3"));
            assertTrue(index.contains("unreadable\t0"));
        }
    }

    @Test public void unsafeArchiveNamesAbortAndRemovePartialOutput() throws Exception {
        File original = source("code.jar", "abc");
        for (String entry : Arrays.asList("firmware/../outside", "/absolute", "firmware/a\\b", "firmware/a\nname", "firmware//b")) {
            File out = tmp.newFolder();
            assertThrows(IOException.class, () -> InterfaceBundle.write(out, "report", Collections.singletonList(
                new InterfaceBundle.Source(original, entry))));
            assertEquals(0, out.list().length);
        }
    }

    @Test public void cancellationDoesNotLeaveAShareablePartialZip() throws Exception {
        File out = tmp.newFolder();
        Thread.currentThread().interrupt();
        try {
            assertThrows(java.io.InterruptedIOException.class, () -> InterfaceBundle.write(out, "report", Collections.emptyList()));
            assertEquals(0, out.list().length);
        } finally { Thread.interrupted(); }
    }

    @Test public void duplicateCandidatesProduceOneArchiveEntry() throws Exception {
        InterfaceBundle.Source item = new InterfaceBundle.Source(source("code.jar", "abc"), "firmware/system/code.jar");
        File archive = InterfaceBundle.write(tmp.newFolder(), "report", Arrays.asList(item, item));
        try (ZipFile zip = new ZipFile(archive)) { assertEquals(3, zip.size()); }
    }

    @Test public void absentFirmwareStillProducesAnExplicitlyIncompleteReport() throws Exception {
        File archive = InterfaceBundle.write(tmp.newFolder(), "directoryUnavailable=/system/framework", Collections.emptyList());
        try (ZipFile zip = new ZipFile(archive)) {
            assertEquals(2, zip.size());
            assertTrue(read(zip, "report.txt").contains("directoryUnavailable="));
            assertEquals("status\tbytes\tsha256\tentry\n", read(zip, "files.tsv"));
        }
    }

    private static String read(ZipFile zip, String name) throws IOException {
        try (java.io.InputStream stream = zip.getInputStream(zip.getEntry(name))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
