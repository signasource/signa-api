package com.signasource.signa_api.admin.controller;

import com.signasource.signa_api.admin.dto.AdminSignDetailResponse;
import com.signasource.signa_api.admin.dto.AdminSignResponse;
import com.signasource.signa_api.admin.dto.CreateAdminSignRequest;
import com.signasource.signa_api.admin.dto.UpdateSignRequest;
import com.signasource.signa_api.admin.service.AdminSignService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Sign catalog editor of the admin panel (signa-web /admin/signs). ADMIN only (/admin/**). */
@RestController
@RequestMapping("/admin/signs")
@RequiredArgsConstructor
public class AdminSignController {

    private final AdminSignService adminSignService;

    @GetMapping
    public ResponseEntity<Page<AdminSignResponse>> list(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 30, sort = "meaning", direction = Sort.Direction.ASC)
                    Pageable pageable) {
        return ResponseEntity.ok(adminSignService.list(query, pageable));
    }

    @PostMapping
    public ResponseEntity<AdminSignDetailResponse> create(
            @Valid @RequestBody CreateAdminSignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminSignService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminSignDetailResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(adminSignService.detail(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AdminSignDetailResponse> update(
            @PathVariable UUID id, @RequestBody UpdateSignRequest request) {
        return ResponseEntity.ok(adminSignService.update(id, request));
    }

    /**
     * Multipart field {@code file}: a .glb or .mp4 (max 50 MB). {@code view=lateral} uploads the
     * side-view video of an .mp4 sign.
     */
    @PostMapping(value = "/{id}/animation", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminSignDetailResponse> uploadAnimation(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "front") String view) {
        AdminSignService.View target =
                "lateral".equalsIgnoreCase(view)
                        ? AdminSignService.View.LATERAL
                        : AdminSignService.View.FRONT;
        return ResponseEntity.ok(adminSignService.uploadAnimation(id, file, target));
    }

    @DeleteMapping("/{id}/animation/lateral")
    public ResponseEntity<AdminSignDetailResponse> removeLateral(@PathVariable UUID id) {
        return ResponseEntity.ok(adminSignService.removeLateral(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminSignService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
