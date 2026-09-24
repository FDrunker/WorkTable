package com.ruoyi.web.controller.knowledge;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.domain.CustomerServiceChatRequest;
import com.ruoyi.knowledge.domain.CustomerServiceConfigRequest;
import com.ruoyi.knowledge.service.CustomerServiceService;

@RestController
@RequestMapping("/knowledge/ai-chat")
public class CustomerServiceController extends BaseController
{
    private final CustomerServiceService customerService;

    public CustomerServiceController(CustomerServiceService customerService)
    {
        this.customerService = customerService;
    }

    @PreAuthorize("@ss.hasPermi('ai:chat:list')")
    @GetMapping("/config")
    public AjaxResult getConfig()
    {
        return AjaxResult.success(customerService.getConfig());
    }

    @PreAuthorize("@ss.hasPermi('ai:chat:list')")
    @GetMapping("/options")
    public AjaxResult options()
    {
        return AjaxResult.success(customerService.options());
    }

    @PreAuthorize("@ss.hasPermi('ai:chat:edit')")
    @Log(title = "AI对话设置", businessType = BusinessType.UPDATE)
    @PutMapping("/config")
    public AjaxResult saveConfig(@RequestBody CustomerServiceConfigRequest request)
    {
        return AjaxResult.success(customerService.saveConfig(request, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('ai:chat:chat')")
    @PostMapping("/chat")
    public AjaxResult chat(@RequestBody CustomerServiceChatRequest request)
    {
        return AjaxResult.success(customerService.chat(request));
    }
}
