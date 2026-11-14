package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.signasource.signa_api.config.R2Properties;
import com.signasource.signa_api.config.SignMediaProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.S3Client;

/** Local-directory mode (no R2 credentials), which is what dev and the demo run. */
class SignMediaStorageTest {

    private static final String PUBLIC = "https://pub.example.r2.dev";

    @TempDir Path dir;
    private SignMediaStorage storage;

    @BeforeEach
    void setUp() {
        storage =
                new SignMediaStorage(
                        mock(S3Client.class),
                        new R2Properties(null, null, null, "bucket", 15),
                        new SignMediaProperties(PUBLIC + "/", dir.toString()));
    }

    @Test
    void isLocalWithoutCredentials() {
        assertFalse(storage.isRemote());
    }

    @Test
    void replaceWritesAndBacksUpThePreviousFile() throws IOException {
        storage.replace("lsa/hola.glb", new byte[] {1}, "model/gltf-binary");
        storage.replace("lsa/hola.glb", new byte[] {2}, "model/gltf-binary");

        assertArrayEquals(new byte[] {2}, Files.readAllBytes(dir.resolve("lsa/hola.glb")));
        try (Stream<Path> history = Files.list(dir.resolve("lsa/_history"))) {
            List<Path> files = history.toList();
            assertEquals(1, files.size());
            assertTrue(files.get(0).getFileName().toString().matches("hola-\\d+\\.glb"));
            assertArrayEquals(new byte[] {1}, Files.readAllBytes(files.get(0)));
        }
    }

    @Test
    void copyReturnsFalseWhenTheSourceIsNotStoredLocally() {
        assertFalse(storage.copy("lsa/hola.glb", "lsa/saludo.glb"));

        storage.replace("lsa/hola.glb", new byte[] {1}, "model/gltf-binary");
        assertTrue(storage.copy("lsa/hola.glb", "lsa/saludo.glb"));
        assertTrue(storage.exists("lsa/saludo.glb"));
    }

    @Test
    void publicUrlFallsBackToTheBucketAndAddsTheVersion() {
        assertEquals(
                PUBLIC + "/lsa/por%20favor.glb?v=1700000000",
                storage.publicUrl("lsa/por favor.glb", Instant.ofEpochSecond(1_700_000_000L)));
        assertEquals(PUBLIC + "/lsa/hola.glb", storage.publicUrl("lsa/hola.glb", null));
    }

    @Test
    void rejectsKeysThatEscapeTheDirectory() {
        assertThrows(IllegalArgumentException.class, () -> storage.exists("../secret"));
    }

    @Test
    void formatAndKeys() {
        assertEquals("mp4", SignMediaStorage.formatOf("lsa/hola.MP4"));
        assertEquals("glb", SignMediaStorage.formatOf("lsa/hola.glb"));
        assertNull(SignMediaStorage.formatOf(" "));
        assertEquals("lsa/Hola.mp4", SignMediaStorage.defaultKey("LSA", "Hola", "mp4"));
        assertEquals(
                "lsa/_history/hola-5.glb",
                SignMediaStorage.historyKey("lsa/hola.glb", Instant.ofEpochMilli(5)));
    }
}
