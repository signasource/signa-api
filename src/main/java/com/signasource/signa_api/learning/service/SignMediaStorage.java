package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.config.R2Properties;
import com.signasource.signa_api.config.SignMediaProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Reads and writes sign animation files (.glb / .mp4) by object key ({@code lsa/hola.glb}).
 *
 * <p>With R2 credentials configured, files go to the R2 bucket and clients get the bucket's public
 * URL. Without them (local/demo) files go to {@link SignMediaProperties#localDir()} and are served
 * by the API under {@code /media/**}; keys that were never uploaded locally still resolve to the
 * public bucket, which is where every imported default .glb already lives.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignMediaStorage {

    public static final String MEDIA_PATH = "/media";
    public static final String GLB = "glb";
    public static final String MP4 = "mp4";
    private static final String HISTORY_DIR = "_history";

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private final S3Client r2Client;
    private final R2Properties r2Properties;
    private final SignMediaProperties properties;

    /** True when writes go to R2; false = local directory fallback. */
    public boolean isRemote() {
        return notBlank(r2Properties.accessKeyId()) && notBlank(r2Properties.secretAccessKey());
    }

    /**
     * Writes {@code bytes} at {@code key}. An existing object is first copied to {@code
     * <dir>/_history/<name>-<epochMillis>.<ext>} so a bad upload can be rolled back by hand.
     */
    public void replace(String key, byte[] bytes, String contentType) {
        if (exists(key) && !copy(key, historyKey(key, Instant.now()))) {
            throw new IllegalStateException("Could not back up the previous file at " + key);
        }
        if (isRemote()) {
            r2Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(r2Properties.bucket())
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(bytes));
            return;
        }
        Path target = localPath(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + target, e);
        }
    }

    /**
     * Copies {@code from} to {@code to} within the same store. Returns false when the source is not
     * there (or, locally, when it only exists in the public bucket and cannot be copied).
     */
    public boolean copy(String from, String to) {
        if (isRemote()) {
            try {
                r2Client.copyObject(
                        CopyObjectRequest.builder()
                                .sourceBucket(r2Properties.bucket())
                                .sourceKey(from)
                                .destinationBucket(r2Properties.bucket())
                                .destinationKey(to)
                                .build());
                return true;
            } catch (S3Exception e) {
                if (e.statusCode() == 404) {
                    return false;
                }
                throw e;
            }
        }
        Path source = localPath(from);
        if (!Files.isRegularFile(source)) {
            return false;
        }
        Path target = localPath(to);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy " + source + " to " + target, e);
        }
    }

    public boolean exists(String key) {
        if (isRemote()) {
            try {
                r2Client.headObject(
                        HeadObjectRequest.builder().bucket(r2Properties.bucket()).key(key).build());
                return true;
            } catch (S3Exception e) {
                if (e.statusCode() == 404) {
                    return false;
                }
                throw e;
            }
        }
        return Files.isRegularFile(localPath(key));
    }

    /**
     * Whether a client would actually find the file: imported signs point at {@code
     * lsa/<meaning>.glb} even when it was never uploaded. Locally (no credentials) a key not stored
     * in the local directory is checked with a HEAD on the public bucket. Null when it cannot tell.
     */
    public Boolean isAvailable(String key) {
        try {
            if (isRemote() || Files.isRegularFile(localPath(key))) {
                return exists(key);
            }
            HttpRequest head =
                    HttpRequest.newBuilder(URI.create(publicUrl(key, null)))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody())
                            .timeout(Duration.ofSeconds(3))
                            .build();
            int status = HTTP.send(head, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 300;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException e) {
            log.warn("Could not check whether '{}' exists: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * URL a client can GET without credentials. {@code version} (the last upload) is appended as
     * {@code ?v=<epochSeconds>} so caches keyed by URL pick up a re-upload under the same key.
     */
    public String publicUrl(String key, Instant version) {
        String base = properties.publicBaseUrl().replaceAll("/+$", "");
        if (!isRemote() && Files.isRegularFile(localPath(key))) {
            base = localBaseUrl(base);
        }
        String url = base + "/" + encodeKey(key);
        return version == null ? url : url + "?v=" + version.getEpochSecond();
    }

    /** Absolute root of the local fallback directory (served under {@link #MEDIA_PATH}). */
    public Path localRoot() {
        return Path.of(properties.localDir()).toAbsolutePath().normalize();
    }

    /** {@code mp4} or {@code glb} from the object key's extension; null when there is no key. */
    public static String formatOf(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return objectKey.toLowerCase(Locale.ROOT).endsWith(".mp4") ? MP4 : GLB;
    }

    /** {@code <language code>/<meaning>.<format>}: the key the importer and the app derive too. */
    public static String defaultKey(String signLanguageCode, String meaning, String format) {
        return signLanguageCode.toLowerCase(Locale.ROOT) + "/" + meaning + "." + format;
    }

    static String historyKey(String key, Instant at) {
        int slash = key.lastIndexOf('/');
        String dir = slash < 0 ? "" : key.substring(0, slash + 1);
        String file = key.substring(slash + 1);
        int dot = file.lastIndexOf('.');
        String name = dot < 0 ? file : file.substring(0, dot);
        String ext = dot < 0 ? "" : file.substring(dot);
        return dir + HISTORY_DIR + "/" + name + "-" + at.toEpochMilli() + ext;
    }

    static String encodeKey(String key) {
        return Arrays.stream(key.split("/"))
                .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
    }

    private Path localPath(String key) {
        Path root = localRoot();
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Object key escapes the media directory: " + key);
        }
        return path;
    }

    private static String localBaseUrl(String fallback) {
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path(MEDIA_PATH)
                    .build()
                    .toUriString();
        } catch (IllegalStateException noRequest) {
            return fallback;
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
