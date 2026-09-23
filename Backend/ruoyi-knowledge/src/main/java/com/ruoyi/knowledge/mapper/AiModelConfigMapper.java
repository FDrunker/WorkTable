package com.ruoyi.knowledge.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.knowledge.domain.AiModelConfig;

public interface AiModelConfigMapper
{
    List<AiModelConfig> selectModelList(AiModelConfig query);

    List<AiModelConfig> selectEnabledModels(@Param("modelType") String modelType);

    AiModelConfig selectModelById(@Param("modelId") Long modelId);

    int insertModel(AiModelConfig model);

    int updateModel(AiModelConfig model);

    int deleteModels(@Param("modelIds") Long[] modelIds);

    int clearDefault(@Param("modelType") String modelType, @Param("exceptModelId") Long exceptModelId);

    int updateTestResult(AiModelConfig model);
}
