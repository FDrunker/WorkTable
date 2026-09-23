package com.ruoyi.knowledge.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModelProviderTest
{
    @Test
    void enforcesProviderCapabilities()
    {
        assertTrue(ModelProvider.OPENAI.supports("embedding"));
        assertTrue(ModelProvider.OPENAI.supports("chat"));
        assertFalse(ModelProvider.DEEPSEEK.supports("embedding"));
        assertTrue(ModelProvider.DEEPSEEK.supports("chat"));
        assertTrue(ModelProvider.TYPESAFE.supports("system-one"));
        assertFalse(ModelProvider.TYPESAFE.supports("embedding"));
    }
}
