package com.farmlive.web;

import com.farmlive.auth.AuthInterceptor;
import com.farmlive.auth.CurrentUser;
import com.farmlive.common.ApiResponse;
import com.farmlive.common.BusinessException;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.AdminRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 农户本人档案，用于预约页回填所属县域。
 */
@RestController
@RequestMapping("/api/farmer/profile")
public class FarmerProfileController {
    private final AdminRepository repository;

    public FarmerProfileController(AdminRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<Dtos.FarmerView> profile(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user) {
        if (user.farmerId() == null) {
            throw BusinessException.badRequest("当前账号未关联农户");
        }
        return ApiResponse.ok(repository.findFarmer(user.farmerId())
                .orElseThrow(() -> BusinessException.notFound("农户档案不存在")));
    }
}
