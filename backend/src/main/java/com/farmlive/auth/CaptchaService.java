package com.farmlive.auth;

import com.farmlive.common.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 登录验证码：服务端生成简单算术题，一次性使用，5 分钟过期。
 */
@Service
public class CaptchaService {
    private record Captcha(String answer, LocalDateTime expiresAt) {
    }

    private final Map<String, Captcha> captchas = new ConcurrentHashMap<>();

    public Map<String, String> generate() {
        cleanup();
        int left = ThreadLocalRandom.current().nextInt(1, 10);
        int right = ThreadLocalRandom.current().nextInt(1, 10);
        String id = UUID.randomUUID().toString().replace("-", "");
        captchas.put(id, new Captcha(String.valueOf(left + right), LocalDateTime.now().plusMinutes(5)));
        return Map.of("captchaId", id, "question", left + " + " + right + " = ?");
    }

    public void verify(String id, String answer) {
        if (id == null || answer == null) {
            throw BusinessException.badRequest("CAPTCHA_REQUIRED", "请输入验证码");
        }
        Captcha captcha = captchas.remove(id);
        if (captcha == null || captcha.expiresAt().isBefore(LocalDateTime.now())) {
            throw BusinessException.badRequest("CAPTCHA_EXPIRED", "验证码已失效，请刷新");
        }
        if (!captcha.answer().equals(answer.trim())) {
            throw BusinessException.badRequest("CAPTCHA_INVALID", "验证码错误");
        }
    }

    private void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        captchas.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }
}
