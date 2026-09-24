package com.ruoyi.knowledge.mapper;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.knowledge.domain.CustomerServiceConfig;

public interface CustomerServiceConfigMapper
{
    CustomerServiceConfig selectConfig(@Param("scene") String scene);

    int insertConfig(CustomerServiceConfig config);

    int updateConfig(CustomerServiceConfig config);
}
