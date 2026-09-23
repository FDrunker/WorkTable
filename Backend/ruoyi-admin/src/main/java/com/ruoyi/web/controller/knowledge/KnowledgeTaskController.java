package com.ruoyi.web.controller.knowledge;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.domain.KnowledgeTask;
import com.ruoyi.knowledge.service.KnowledgeTaskService;

@RestController
@RequestMapping("/knowledge/task")
public class KnowledgeTaskController extends BaseController
{
    private final KnowledgeTaskService taskService;

    public KnowledgeTaskController(KnowledgeTaskService taskService)
    {
        this.taskService = taskService;
    }

    @PreAuthorize("@ss.hasPermi('knowledge:task:list')")
    @GetMapping("/list")
    public TableDataInfo list(KnowledgeTask query)
    {
        startPage();
        List<KnowledgeTask> list = taskService.list(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('knowledge:task:list')")
    @GetMapping("/{taskId}")
    public AjaxResult get(@PathVariable Long taskId)
    {
        return AjaxResult.success(taskService.get(taskId));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:task:cancel')")
    @Log(title = "知识库任务", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/cancel")
    public AjaxResult cancel(@PathVariable Long taskId)
    {
        return toAjax(taskService.requestCancel(taskId, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:task:retry')")
    @Log(title = "知识库任务", businessType = BusinessType.OTHER)
    @PostMapping("/{taskId}/retry")
    public AjaxResult retry(@PathVariable Long taskId)
    {
        return AjaxResult.success(taskService.retry(taskId, getUsername()));
    }
}
