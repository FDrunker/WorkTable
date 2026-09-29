package com.ruoyi.knowledge.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.domain.AiModelConfig;
import com.ruoyi.knowledge.service.AiModelService;

@RestController
@RequestMapping("/knowledge/model")
public class AiModelController extends BaseController
{
    private final AiModelService modelService;

    public AiModelController(AiModelService modelService)
    {
        this.modelService = modelService;
    }

    @PreAuthorize("@ss.hasPermi('knowledge:model:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiModelConfig query)
    {
        startPage();
        List<AiModelConfig> list = modelService.list(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('knowledge:model:query')")
    @GetMapping("/{modelId}")
    public AjaxResult get(@PathVariable Long modelId)
    {
        return AjaxResult.success(modelService.get(modelId));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:model:list')")
    @GetMapping("/options")
    public AjaxResult options(@RequestParam(required = false) String modelType)
    {
        return AjaxResult.success(modelService.options(modelType));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:model:list')")
    @GetMapping("/providers")
    public AjaxResult providers()
    {
        return AjaxResult.success(modelService.providerCatalog());
    }

    @PreAuthorize("@ss.hasPermi('knowledge:model:add')")
    @Log(title = "AI模型配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiModelConfig model)
    {
        return AjaxResult.success(modelService.create(model, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:model:edit')")
    @Log(title = "AI模型配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiModelConfig model)
    {
        return AjaxResult.success(modelService.update(model, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:model:remove')")
    @Log(title = "AI模型配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{modelIds}")
    public AjaxResult remove(@PathVariable Long[] modelIds)
    {
        return toAjax(modelService.delete(modelIds));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:model:test')")
    @Log(title = "AI模型连通性测试", businessType = BusinessType.OTHER)
    @PostMapping("/{modelId}/test")
    public AjaxResult test(@PathVariable Long modelId)
    {
        return AjaxResult.success(modelService.test(modelId));
    }
}
