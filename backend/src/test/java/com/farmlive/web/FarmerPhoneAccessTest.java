package com.farmlive.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmlive.auth.*;
import com.farmlive.common.GlobalExceptionHandler;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.AdminRepository;
import com.farmlive.repo.RegionRepository;
import com.farmlive.service.AuditService;
import com.farmlive.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FarmerPhoneAccessTest {
    private final TokenStore sessions = new TokenStore(8);
    private final VerifyTokenService verifyTokens = new VerifyTokenService();
    private final AdminRepository repository = mock(AdminRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        AdminController admin = new AdminController(repository, mock(PasswordService.class),
                mock(RegionRepository.class), mock(NotificationService.class), audit, verifyTokens);
        mvc = MockMvcBuilders.standaloneSetup(new LiveBasicDataController(admin), admin)
                .addInterceptors(new AuthInterceptor(sessions, new ObjectMapper()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        Dtos.FarmerView farmer = mock(Dtos.FarmerView.class);
        when(farmer.id()).thenReturn(7L);
        when(farmer.farmerNo()).thenReturn("NF007");
        when(farmer.name()).thenReturn("测试农户");
        when(repository.findFarmer(7L)).thenReturn(Optional.of(farmer));
        when(repository.findFarmerPhone(7L)).thenReturn(Optional.of("13800000000"));
    }

    @Test
    void liveAdminMustVerifyAndTokenCanBeUsedOnlyOnce() throws Exception {
        CurrentUser user = user(1L, "live_admin");
        String session = sessions.create(user);
        mvc.perform(get("/api/live/farmers/7/phone").header("Authorization", "Bearer " + session))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("VERIFY_REQUIRED"));
        String token = verifyTokens.issue(user.id());
        mvc.perform(get("/api/live/farmers/7/phone").header("Authorization", "Bearer " + session)
                        .param("verifyToken", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phone").value("13800000000"));
        verify(audit).recordSensitive(eq(user), eq("SENSITIVE_PHONE_VIEW"), anyString());
        mvc.perform(get("/api/live/farmers/7/phone").header("Authorization", "Bearer " + session)
                        .param("verifyToken", token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("VERIFY_REQUIRED"));
    }

    @Test
    void systemAdminCanUseLiveReadRouteButFarmerCannot() throws Exception {
        CurrentUser admin = user(2L, "system_admin");
        mvc.perform(get("/api/live/farmers/7/phone")
                        .header("Authorization", "Bearer " + sessions.create(admin))
                        .param("verifyToken", verifyTokens.issue(admin.id())))
                .andExpect(status().isOk());
        CurrentUser farmer = user(3L, "farmer");
        mvc.perform(get("/api/live/farmers/7/phone")
                        .header("Authorization", "Bearer " + sessions.create(farmer))
                        .param("verifyToken", verifyTokens.issue(farmer.id())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/farmers/7/phone")
                        .header("Authorization", "Bearer " + sessions.create(user(4L, "live_admin"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void verificationTokenIsBoundToItsAccount() throws Exception {
        CurrentUser live = user(1L, "live_admin");
        mvc.perform(get("/api/live/farmers/7/phone")
                        .header("Authorization", "Bearer " + sessions.create(live))
                        .param("verifyToken", verifyTokens.issue(2L)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("VERIFY_REQUIRED"));
        verify(repository, never()).findFarmerPhone(anyLong());
    }

    private CurrentUser user(Long id, String role) {
        return new CurrentUser(id, "user" + id, "用户", role, role, null, null, false);
    }
}
