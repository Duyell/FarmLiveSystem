package com.farmlive.web;

import com.farmlive.common.ApiResponse;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.RegionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 区域字典接口：省 / 市 / 县逐级查询。
 * 权限为全部登录用户，由 AuthInterceptor 统一拦截（只放行登录和健康检查）。
 */
@RestController
@RequestMapping("/api/regions")
public class RegionController {
    private final RegionRepository repository;

    public RegionController(RegionRepository repository) {
        this.repository = repository;
    }

    /**
     * parentCode 为空返回省级；传省级编码返回市；传市级编码返回县。
     * 只返回 enabled = 1 的区域，按编码排序。
     */
    @GetMapping
    public ApiResponse<List<Dtos.RegionView>> list(@RequestParam(required = false) String parentCode) {
        String parent = (parentCode == null || parentCode.isBlank()) ? null : parentCode.trim();
        return ApiResponse.ok(repository.listChildren(parent));
    }
}
