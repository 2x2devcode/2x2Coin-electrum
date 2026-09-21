package com.x2x.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.After;
import org.junit.Test;

public class AppLogTest {

    @After
    public void reset() {
        AppLog.setLogDirectory(null);
    }

    @Test
    public void writesToConfiguredDirectory() throws Exception {
        Path dir = Files.createTempDirectory("x2x-log-test");
        AppLog.setLogDirectory(dir);
        AppLog.info("hello-send-diag");
        Path file = AppLog.logFile();
        assertEquals(dir.resolve("wallet.log"), file);
        assertTrue(Files.isRegularFile(file));
        String body = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(body.contains("hello-send-diag"));
        assertTrue(body.contains("INFO"));
        assertTrue(body.contains(AppLog.APP_VERSION));
    }

    @Test
    public void truncateAddsLengthMarker() {
        assertEquals("abc", AppLog.truncate("abc", 10));
        String t = AppLog.truncate("abcdefghij", 4);
        assertTrue(t.startsWith("abcd"));
        assertTrue(t.contains("len=10"));
    }
}
