package com.ruoyi.web.controller.knowledge;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.knowledge.domain.ResumeAiSendRequest;
import com.ruoyi.knowledge.service.ResumeAiChatService;

/**
 * 免登录个人简历 AI 接口。
 */
@Anonymous
@RestController
@RequestMapping("/public/resume-ai")
public class PublicResumeAiController extends BaseController
{
    private final ResumeAiChatService resumeAiChatService;

    public PublicResumeAiController(ResumeAiChatService resumeAiChatService)
    {
        this.resumeAiChatService = resumeAiChatService;
    }

    @GetMapping("/bootstrap")
    public AjaxResult bootstrap(@RequestParam(required = false) String visitorId)
    {
        return AjaxResult.success(resumeAiChatService.bootstrap(visitorId));
    }

    @PostMapping("/conversations")
    public AjaxResult createConversation(@RequestParam String visitorId)
    {
        return AjaxResult.success(resumeAiChatService.createConversation(visitorId));
    }

    @GetMapping("/conversations")
    public AjaxResult conversations(@RequestParam String visitorId)
    {
        return AjaxResult.success(resumeAiChatService.listConversations(visitorId));
    }

    @GetMapping("/conversations/{conversationId}")
    public AjaxResult conversation(@PathVariable String conversationId, @RequestParam String visitorId)
    {
        return AjaxResult.success(resumeAiChatService.getConversation(conversationId, visitorId));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public AjaxResult deleteConversation(@PathVariable String conversationId, @RequestParam String visitorId)
    {
        resumeAiChatService.deleteConversation(conversationId, visitorId);
        return AjaxResult.success();
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public AjaxResult sendMessage(@PathVariable String conversationId,
            @RequestBody ResumeAiSendRequest request)
    {
        return AjaxResult.success(resumeAiChatService.sendMessage(conversationId, request));
    }
}
