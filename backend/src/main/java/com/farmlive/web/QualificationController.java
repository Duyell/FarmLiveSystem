package com.farmlive.web;

import com.farmlive.auth.AuthInterceptor;
import com.farmlive.auth.CurrentUser;
import com.farmlive.common.ApiResponse;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.QualificationRepository;
import com.farmlive.service.QualificationService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * 直播管理员维护农户资质材料，系统管理员也可用。
 */
@RestController
@RequestMapping("/api/live")
public class QualificationController {
    private final QualificationService service;

    public QualificationController(QualificationService service) {
        this.service = service;
    }

    @GetMapping("/farmers/{farmerId}/qualifications")
    public ApiResponse<List<Dtos.QualificationView>> list(@PathVariable Long farmerId) {
        return ApiResponse.ok(service.list(farmerId));
    }

    @PostMapping("/farmers/{farmerId}/qualifications")
    public ApiResponse<Dtos.QualificationView> upload(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @PathVariable Long farmerId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(service.upload(user, farmerId, file));
    }

    @PostMapping("/qualifications/{id}/review")
    public ApiResponse<Dtos.QualificationView> review(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @PathVariable Long id,
            @Valid @RequestBody Dtos.QualificationReviewRequest request) {
        return ApiResponse.ok(service.review(user, id, request));
    }

    @GetMapping("/qualifications/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id) {
        QualificationRepository.QualificationRow row = service.row(id);
        Path path = service.file(id);
        FileSystemResource resource = new FileSystemResource(path);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                row.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : row.contentType()));
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(row.originalName(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(resource);
    }
}
