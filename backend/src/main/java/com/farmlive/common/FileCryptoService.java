package com.farmlive.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * 文件内容加密（AES-256-GCM）：
 * 密钥由 app.file-secret（环境变量 APP_FILE_SECRET）派生，不入仓库；
 * 加密文件带 "ENCF1" 魔数头，无魔数的历史明文文件下载时原样返回，兼容存量数据。
 */
@Service
public class FileCryptoService {
    private static final byte[] MAGIC = "ENCF1".getBytes(StandardCharsets.US_ASCII);
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public FileCryptoService(@Value("${app.file-secret:FarmLive-Default-File-Secret-2026}") String secret) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("文件加密密钥初始化失败", ex);
        }
    }

    /**
     * 明文 → "ENCF1" + 12B IV + 密文。
     */
    public byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain);
            byte[] result = new byte[MAGIC.length + IV_LENGTH + cipherText.length];
            System.arraycopy(MAGIC, 0, result, 0, MAGIC.length);
            System.arraycopy(iv, 0, result, MAGIC.length, IV_LENGTH);
            System.arraycopy(cipherText, 0, result, MAGIC.length + IV_LENGTH, cipherText.length);
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("文件加密失败", ex);
        }
    }

    /**
     * "ENCF1" 开头 → 解密；否则视为历史明文文件原样返回。
     */
    public byte[] decrypt(byte[] stored) {
        if (!looksEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] iv = Arrays.copyOfRange(stored, MAGIC.length, MAGIC.length + IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(stored, MAGIC.length + IV_LENGTH, stored.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(cipherText);
        } catch (Exception ex) {
            throw new IllegalStateException("文件解密失败（密钥不匹配？）", ex);
        }
    }

    private boolean looksEncrypted(byte[] data) {
        if (data == null || data.length < MAGIC.length) return false;
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) return false;
        }
        return true;
    }
}
