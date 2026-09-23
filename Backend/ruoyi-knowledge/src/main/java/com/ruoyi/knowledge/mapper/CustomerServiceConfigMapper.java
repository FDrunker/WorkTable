package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.domain.CustomerServiceConfig;

public interface CustomerServiceConfigMapper
{
    CustomerServiceConfig selectConfig();

    int insertConfig(CustomerServiceConfig config);

    int updateConfig(CustomerServiceConfig config);
}
