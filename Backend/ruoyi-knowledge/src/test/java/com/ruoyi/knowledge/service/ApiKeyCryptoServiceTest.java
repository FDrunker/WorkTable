package com.ruoyi.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ruoyi.knowledge.config.KnowledgeProperties;

class ApiKeyCryptoServiceTest
{
    private ApiKeyCryptoService cryptoService;

    @BeforeEach
    void setUp()
    {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setEncryptionKey("0rRoJPJP7TmDqHquCC6PW3ytNd7l93zAkkIPSNV83xc=");
        cryptoService = new ApiKeyCryptoService(properties);
    }

    @Test
    void encryptsWithRandomIvAndDecrypts()
    {
        String plainText = "sk-test-secret";

        String first = cryptoService.encrypt(plainText);
        String second = cryptoService.encrypt(plainText);

        assertNotEquals(plainText, first);
        assertNotEquals(first, second);
        assertEquals(plainText, cryptoService.decrypt(first));
        assertEquals(plainText, cryptoService.decrypt(second));
    }

    @Test
    void masksAllButLastFourCharacters()
    {
        assertTrue(cryptoService.mask("sk-12345678").endsWith("5678"));
        assertEquals("****", cryptoService.mask("1234"));
    }
}
