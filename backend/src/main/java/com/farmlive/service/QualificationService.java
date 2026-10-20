package com.farmlive.service;

import com.farmlive.auth.CurrentUser;
import com.farmlive.common.BusinessException;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.QualificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * 农户资质材料：上传、列表、审核、下载。
 * 文件保存在 app.upload-dir 下，数据库只存元数据。
 */
@Service
public class QualificationService {
    private final QualificationRepository repository;
    private final AuditService auditService;
    private final Path uploadRoot;

    public QualificationService(QualificationRepository repository,
                                AuditService auditService,
                                @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.repository = repository;
        this.auditService = auditService;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public List<Dtos.QualificationView> list(Long farmerId) {
        if (!repository.farmerExists(farmerId)) {
            throw BusinessException.notFound("农户不存在");
        }
        return repository.listByFarmer(farmerId);
    }

    @Transactional
    public Dtos.QualificationView upload(CurrentUser user, Long farmerId, MultipartFile file) {
        if (!repository.farmerExists(farmerId)) {
            throw BusinessException.notFound("农户不存在");
        }
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择要上传的文件");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw BusinessException.badRequest("文件不能超过 10MB");
        }
        String original = file.getOriginalFilename() == null ? "material" : file.getOriginalFilename();
        original = Paths.get(original).getFileName().toString();
        String lower = original.toLowerCase();
        if (!(lower.endsWith(".pdf") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".docx"))) {
            throw BusinessException.badRequest("只支持 PDF、JPG、PNG、DOCX");
        }
        String suffix = lower.substring(lower.lastIndexOf('.'));
        String stored = UUID.randomUUID().toString().replace("-", "") + suffix;
        Path dir = uploadRoot.resolve("qualifications").resolve(String.valueOf(farmerId));
        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw BusinessException.badRequest("文件保存失败");
        }
        Long id = repository.insert(farmerId, original, stored,
                file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                file.getSize(), user == null ? null : user.id());
        auditService.record(user, "QUALIFICATION_UPLOAD",
                "农户 " + farmerId + " 上传资质材料 " + original);
        return repository.listByFarmer(farmerId).stream()
                .filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> BusinessException.notFound("资质材料保存失败"));
    }

    @Transactional
    public Dtos.QualificationView review(CurrentUser user, Long id, Dtos.QualificationReviewRequest request) {
        QualificationRepository.QualificationRow row = repository.findRow(id)
                .orElseThrow(() -> BusinessException.notFound("资质材料不存在"));
        if (!"pending".equals(row.status())) {
            throw BusinessException.conflict("QUALIFICATION_STATUS_INVALID", "该材料已经审核");
        }
        String result = request.result().trim();
        String comment = request.comment() == null ? null : request.comment().trim();
        String status;
        if ("approve".equals(result)) {
            status = "approved";
        } else if ("reject".equals(result)) {
            if (comment == null || comment.length() < 5) {
                throw BusinessException.badRequest("驳回意见至少 5 个字");
            }
            status = "rejected";
        } else {
            throw BusinessException.badRequest("审核结果只能是 approve 或 reject");
        }
        repository.review(id, status, comment, user == null ? null : user.id(), java.time.LocalDateTime.now());
        repository.updateFarmerQualification(row.farmerId(), "approved".equals(status) ? "verified" : "rejected");
        auditService.record(user, "QUALIFICATION_" + status.toUpperCase(),
                "审核资质材料 " + row.originalName());
        return repository.listByFarmer(row.farmerId()).stream()
                .filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> BusinessException.notFound("资质材料不存在"));
    }

    public Path file(Long id) {
        QualificationRepository.QualificationRow row = repository.findRow(id)
                .orElseThrow(() -> BusinessException.notFound("资质材料不存在"));
        Path path = uploadRoot.resolve("qualifications")
                .resolve(String.valueOf(row.farmerId())).resolve(row.storedName()).normalize();
        if (!path.startsWith(uploadRoot) || !Files.exists(path)) {
            throw BusinessException.notFound("文件不存在");
        }
        return path;
    }

    public QualificationRepository.QualificationRow row(Long id) {
        return repository.findRow(id)
                .orElseThrow(() -> BusinessException.notFound("资质材料不存在"));
    }
}
