package com.signasource.signa_api.admin.service;

import com.signasource.signa_api.admin.dto.AdminSignDetailResponse;
import com.signasource.signa_api.admin.dto.AdminSignResponse;
import com.signasource.signa_api.admin.dto.CreateAdminSignRequest;
import com.signasource.signa_api.admin.dto.SignUsageResponse;
import com.signasource.signa_api.admin.dto.UpdateSignRequest;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.gamification.repository.UserLearnedSignRepository;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.ReportStatus;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.SignReportRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import com.signasource.signa_api.learning.service.SignMediaStorage;
import com.signasource.signa_api.learning.util.SignReferences;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The admin sign editor: catalog, rename, handedness, YouTube link and animation upload.
 *
 * <p>Lessons and learned-sign rows reference a sign by its meaning (text), so a rename is a
 * cascade: see {@link #update}.
 */
@Service
@RequiredArgsConstructor
public class AdminSignService {

    /** Same rule the web GLB viewer applies to sign names (src/lib/glb.ts in signa-web). */
    static final Pattern MEANING_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_\\- ]{1,40}$");

    static final long MAX_UPLOAD_BYTES = 50L * 1024 * 1024;
    private static final String DEFAULT_LANGUAGE = "LSA";
    private static final Pattern YOUTUBE_URL_PATTERN =
            Pattern.compile("^https?://(www\\.|m\\.)?(youtube\\.com|youtu\\.be)/.+");
    private static final List<ReportStatus> OPEN_REPORTS =
            List.of(ReportStatus.PENDING, ReportStatus.REVIEWED);

    private final SignRepository signRepository;
    private final SignLanguageRepository signLanguageRepository;
    private final SignReportRepository signReportRepository;
    private final LessonBlockRepository lessonBlockRepository;
    private final UserLearnedSignRepository userLearnedSignRepository;
    private final SignReferences signReferences;
    private final SignMediaStorage mediaStorage;

    @Transactional(readOnly = true)
    public Page<AdminSignResponse> list(String query, Pageable pageable) {
        Page<Sign> page =
                (query != null && !query.isBlank())
                        ? signRepository.findByMeaningContainingIgnoreCase(query.strip(), pageable)
                        : signRepository.findAll(pageable);
        Map<String, Set<UUID>> lessonsByValue = activeLessonsByValue();
        List<AdminSignResponse> content =
                page.getContent().stream()
                        .map(
                                s ->
                                        AdminSignResponse.from(
                                                s,
                                                signReportRepository.countBySignIdAndStatusIn(
                                                        s.getId(), OPEN_REPORTS),
                                                lessonsByValue
                                                        .getOrDefault(
                                                                SignReferences.normalize(
                                                                        s.getMeaning()),
                                                                Set.of())
                                                        .size()))
                        .toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminSignDetailResponse detail(UUID id) {
        return toDetail(find(id));
    }

    @Transactional
    public AdminSignDetailResponse create(CreateAdminSignRequest request) {
        String meaning = validMeaning(request.meaning());
        if (signRepository.existsByMeaningIgnoreCase(meaning)) {
            throw new ResourceAlreadyInUseException("Sign meaning already in use: " + meaning);
        }
        SignLanguage language =
                signLanguageRepository
                        .findByCode(DEFAULT_LANGUAGE)
                        .orElseThrow(() -> new NotFoundException("Sign language not found"));
        Sign sign =
                Sign.builder()
                        .meaning(meaning)
                        .handedness(request.handedness())
                        .youtubeUrl(validYoutubeUrl(request.youtubeUrl()))
                        .signLanguage(language)
                        .build();
        return toDetail(signRepository.save(sign));
    }

    /**
     * Partial update. Renaming rewrites, in the same transaction, every lesson block config (all
     * course versions, published included: otherwise live lessons would ask for a meaning that no
     * longer exists) and every learned-sign row. A default-named animation ({@code
     * lsa/<old>.<ext>}) is copied to {@code lsa/<new>.<ext>} so app builds that derive the URL from
     * the meaning keep finding it; if it cannot be copied the sign keeps pointing at the old key.
     */
    @Transactional
    public AdminSignDetailResponse update(UUID id, UpdateSignRequest request) {
        Sign sign = find(id);
        if (request.meaning() != null) {
            rename(sign, validMeaning(request.meaning()));
        }
        if (request.handedness() != null) {
            sign.setHandedness(request.handedness());
        }
        if (request.youtubeUrl() != null) {
            sign.setYoutubeUrl(validYoutubeUrl(request.youtubeUrl()));
        }
        return toDetail(signRepository.save(sign));
    }

    /** Which video of an .mp4 sign an upload is for. A .glb sign only has the front one. */
    public enum View {
        FRONT,
        LATERAL
    }

    /**
     * Stores the sign's animation (the format is detected from the file's bytes, not its name). A
     * previous file at the same key is backed up first.
     *
     * <ul>
     *   <li>{@code FRONT}: a .glb or an .mp4 at {@code <lang>/<meaning>.<ext>}. Switching to .glb
     *       drops the lateral video (a 3D avatar can be rotated instead); its file stays.
     *   <li>{@code LATERAL}: an .mp4 at {@code <lang>/<meaning>_lateral.mp4}, only for signs whose
     *       front animation is already an .mp4.
     * </ul>
     */
    @Transactional
    public AdminSignDetailResponse uploadAnimation(UUID id, MultipartFile file, View view) {
        Sign sign = find(id);
        if (view == View.LATERAL
                && !SignMediaStorage.MP4.equals(
                        SignMediaStorage.formatOf(sign.getAnimationUrl()))) {
            throw new InvalidInputException("Upload the front .mp4 video before the lateral one");
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidInputException("The file is empty");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new InvalidInputException("The file exceeds 50 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String format = detectFormat(bytes);
        if (format == null) {
            throw new InvalidInputException("Only .glb (binary glTF) and .mp4 files are accepted");
        }
        if (view == View.LATERAL && !SignMediaStorage.MP4.equals(format)) {
            throw new InvalidInputException("The lateral view must be an .mp4 video");
        }
        String code = sign.getSignLanguage().getCode();
        String key =
                view == View.LATERAL
                        ? SignMediaStorage.defaultKey(code, lateralName(sign.getMeaning()), format)
                        : SignMediaStorage.defaultKey(code, sign.getMeaning(), format);
        mediaStorage.replace(
                key,
                bytes,
                SignMediaStorage.MP4.equals(format) ? "video/mp4" : "model/gltf-binary");
        if (view == View.LATERAL) {
            sign.setLateralAnimationUrl(key);
        } else {
            sign.setAnimationUrl(key);
            if (SignMediaStorage.GLB.equals(format)) {
                sign.setLateralAnimationUrl(null);
            }
        }
        sign.setAnimationUpdatedAt(Instant.now());
        return toDetail(signRepository.save(sign));
    }

    /** Removes the lateral video from the sign (the file stays in the bucket). */
    @Transactional
    public AdminSignDetailResponse removeLateral(UUID id) {
        Sign sign = find(id);
        sign.setLateralAnimationUrl(null);
        return toDetail(signRepository.save(sign));
    }

    /** Only signs no lesson references and nobody reported can be deleted. */
    @Transactional
    public void delete(UUID id) {
        Sign sign = find(id);
        int lessons = usages(sign.getMeaning()).size();
        if (lessons > 0) {
            throw new ResourceAlreadyInUseException(
                    "Sign is used in " + lessons + " lesson(s); remove it from them first");
        }
        if (signReportRepository.existsBySignId(id)) {
            throw new ResourceAlreadyInUseException("Sign has reports and cannot be deleted");
        }
        signRepository.delete(sign);
    }

    /**
     * {@code glb} for binary glTF, {@code mp4} for an ISO-BMFF video (not QuickTime); else null.
     */
    static String detectFormat(byte[] b) {
        if (b.length < 12) {
            return null;
        }
        if (b[0] == 'g' && b[1] == 'l' && b[2] == 'T' && b[3] == 'F') {
            return SignMediaStorage.GLB;
        }
        boolean ftyp = b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
        boolean quickTime = b[8] == 'q' && b[9] == 't';
        return ftyp && !quickTime ? SignMediaStorage.MP4 : null;
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void rename(Sign sign, String to) {
        String from = sign.getMeaning();
        if (from.equals(to)) {
            return;
        }
        signRepository
                .findByMeaningIgnoreCase(to)
                .filter(other -> !other.getId().equals(sign.getId()))
                .ifPresent(
                        other -> {
                            throw new ResourceAlreadyInUseException(
                                    "Sign meaning already in use: " + to);
                        });

        List<LessonBlock> changed = new ArrayList<>();
        for (LessonBlock block :
                lessonBlockRepository.findWithCourseByTypeIn(SignReferences.CATALOG_BLOCK_TYPES)) {
            String config = signReferences.rename(block, from, to);
            if (config != null) {
                block.setConfig(config);
                changed.add(block);
            }
        }
        lessonBlockRepository.saveAll(changed);
        userLearnedSignRepository.renameSign(from, to);

        String code = sign.getSignLanguage().getCode();
        sign.setAnimationUrl(moveDefault(code, sign.getAnimationUrl(), from, to));
        sign.setLateralAnimationUrl(
                moveDefault(
                        code, sign.getLateralAnimationUrl(), lateralName(from), lateralName(to)));
        sign.setMeaning(to);
    }

    /**
     * {@code key} copied to the name derived from {@code to} when it is the default derived from
     * {@code from}; otherwise (custom key, or the copy failed) the same key.
     */
    private String moveDefault(String code, String key, String from, String to) {
        String format = SignMediaStorage.formatOf(key);
        if (format == null) {
            return key;
        }
        String newDefault = SignMediaStorage.defaultKey(code, to, format);
        boolean isDefault = key.equals(SignMediaStorage.defaultKey(code, from, format));
        return isDefault && mediaStorage.copy(key, newDefault) ? newDefault : key;
    }

    static String lateralName(String meaning) {
        return meaning + "_lateral";
    }

    private AdminSignDetailResponse toDetail(Sign sign) {
        String key = sign.getAnimationUrl();
        boolean hasFront = SignMediaStorage.formatOf(key) != null;
        String lateralKey = sign.getLateralAnimationUrl();
        return AdminSignDetailResponse.from(
                sign,
                hasFront ? mediaStorage.publicUrl(key, sign.getAnimationUpdatedAt()) : null,
                hasFront ? mediaStorage.isAvailable(key) : null,
                lateralKey == null
                        ? null
                        : mediaStorage.publicUrl(lateralKey, sign.getAnimationUpdatedAt()),
                signReportRepository.countBySignIdAndStatusIn(sign.getId(), OPEN_REPORTS),
                usages(sign.getMeaning()));
    }

    /** Lessons (any version status) referencing {@code meaning}, in course/version/lesson order. */
    private List<SignUsageResponse> usages(String meaning) {
        String value = SignReferences.normalize(meaning);
        Map<UUID, Lesson> lessons = new LinkedHashMap<>();
        Map<UUID, Set<String>> types = new HashMap<>();
        for (LessonBlock block :
                lessonBlockRepository.findWithCourseByTypeIn(SignReferences.CATALOG_BLOCK_TYPES)) {
            if (signReferences.values(block).contains(value)) {
                Lesson lesson = block.getLesson();
                lessons.putIfAbsent(lesson.getId(), lesson);
                types.computeIfAbsent(lesson.getId(), k -> new LinkedHashSet<>())
                        .add(block.getType().name());
            }
        }
        return lessons.values().stream()
                .sorted(
                        Comparator.comparing(
                                        (Lesson l) ->
                                                l.getTopic()
                                                        .getCourseVersion()
                                                        .getCourse()
                                                        .getName())
                                .thenComparing(
                                        l -> versionNumber(l.getTopic().getCourseVersion()),
                                        Comparator.reverseOrder())
                                .thenComparingInt(l -> l.getTopic().getOrder())
                                .thenComparingInt(Lesson::getOrder))
                .map(
                        l -> {
                            CourseVersion v = l.getTopic().getCourseVersion();
                            return new SignUsageResponse(
                                    v.getCourse().getId(),
                                    v.getCourse().getName(),
                                    v.getId(),
                                    v.getVersion(),
                                    v.getStatus().name(),
                                    l.getTopic().getTitle(),
                                    l.getId(),
                                    l.getName(),
                                    List.copyOf(types.get(l.getId())));
                        })
                .toList();
    }

    /** Lower-cased config value → lessons (draft or published versions) where it appears. */
    private Map<String, Set<UUID>> activeLessonsByValue() {
        Map<String, Set<UUID>> out = new HashMap<>();
        for (LessonBlock block :
                lessonBlockRepository.findWithCourseByTypeIn(SignReferences.CATALOG_BLOCK_TYPES)) {
            if (block.getLesson().getTopic().getCourseVersion().getStatus()
                    == VersionStatus.ARCHIVED) {
                continue;
            }
            for (String value : signReferences.values(block)) {
                out.computeIfAbsent(value, k -> new HashSet<>()).add(block.getLesson().getId());
            }
        }
        return out;
    }

    /** Versions are strings ("1", "2", ... "10"); compare them as numbers when they are. */
    private static long versionNumber(CourseVersion version) {
        try {
            return Long.parseLong(version.getVersion().strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Sign find(UUID id) {
        return signRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Sign not found: " + id));
    }

    private static String validMeaning(String raw) {
        String meaning = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (!MEANING_PATTERN.matcher(meaning).matches()) {
            throw new InvalidInputException(
                    "Invalid meaning: use 1 to 40 letters, numbers, spaces, '-' or '_'");
        }
        return meaning;
    }

    private static String validYoutubeUrl(String raw) {
        String url = (raw == null || raw.isBlank()) ? null : raw.strip();
        if (url != null && !YOUTUBE_URL_PATTERN.matcher(url).matches()) {
            throw new InvalidInputException("Invalid YouTube URL: " + url);
        }
        return url;
    }
}
