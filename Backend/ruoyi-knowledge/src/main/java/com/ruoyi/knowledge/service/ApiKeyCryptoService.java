package com.ruoyi.knowledge.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.knowledge.config.KnowledgeProperties;

/**
 * API Key 的 AES-GCM 加密服务。
 */
@Service
public class ApiKeyCryptoService
{
    private static final int IV_LENGTH = 12;

    private static final int TAG_LENGTH_BIT = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    private final SecretKeySpec secretKey;

    public ApiKeyCryptoService(KnowledgeProperties properties)
    {
        if (StringUtils.isBlank(properties.getEncryptionKey()))
        {
            throw new IllegalStateException("knowledge.encryption-key 未配置");
        }
        byte[] keyBytes;
        try
        {
            keyBytes = Base64.getDecoder().decode(properties.getEncryptionKey().trim());
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalStateException("knowledge.encryption-key 必须是 Base64 编码", e);
        }
        if (keyBytes.length != 32)
        {
            throw new IllegalStateException("knowledge.encryption-key 解码后必须为 32 字节");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plainText)
    {
        if (plainText == null)
        {
            return null;
        }
        try
        {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        }
        catch (GeneralSecurityException e)
        {
            throw new IllegalStateException("API Key 加密失败", e);
        }
    }

    public String decrypt(String cipherText)
    {
        if (StringUtils.isBlank(cipherText))
        {
            return "";
        }
        try
        {
            byte[] payload = Base64.getDecoder().decode(cipherText);
            if (payload.length <= IV_LENGTH)
            {
                throw new IllegalArgumentException("密文长度非法");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        }
        catch (GeneralSecurityException | IllegalArgumentException e)
        {
            throw new IllegalStateException("API Key 解密失败", e);
        }
    }

    public String mask(String plainText)
    {
        if (StringUtils.isBlank(plainText))
        {
            return "";
        }
        if (plainText.length() <= 4)
        {
            return "****";
        }
        return "****" + plainText.substring(plainText.length() - 4);
    }
}
