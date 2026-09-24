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
import com.ruoyi.knowledge.domain.AiChatSendRequest;
import com.ruoyi.knowledge.service.PublicAiChatService;

/**
 * 免登录公开 AI 对话接口。
 */
@Anonymous
@RestController
@RequestMapping("/public/ai-chat")
public class PublicAiChatController extends BaseController
{
    private final PublicAiChatService publicAiChatService;

    public PublicAiChatController(PublicAiChatService publicAiChatService)
    {
        this.publicAiChatService = publicAiChatService;
    }

    @GetMapping("/scenes")
    public AjaxResult scenes()
    {
        return AjaxResult.success(publicAiChatService.listScenes());
    }

    @GetMapping("/{scene}/bootstrap")
    public AjaxResult bootstrap(@PathVariable String scene,
            @RequestParam(required = false) String visitorId)
    {
        return AjaxResult.success(publicAiChatService.bootstrap(scene, visitorId));
    }

    @PostMapping("/{scene}/conversations")
    public AjaxResult createConversation(@PathVariable String scene,
            @RequestParam String visitorId)
    {
        return AjaxResult.success(publicAiChatService.createConversation(scene, visitorId));
    }

    @GetMapping("/{scene}/conversations")
    public AjaxResult conversations(@PathVariable String scene,
            @RequestParam String visitorId)
    {
        return AjaxResult.success(publicAiChatService.listConversations(scene, visitorId));
    }

    @GetMapping("/{scene}/conversations/{conversationId}")
    public AjaxResult conversation(@PathVariable String scene,
            @PathVariable String conversationId,
            @RequestParam String visitorId)
    {
        return AjaxResult.success(publicAiChatService.getConversation(scene, conversationId, visitorId));
    }

    @DeleteMapping("/{scene}/conversations/{conversationId}")
    public AjaxResult deleteConversation(@PathVariable String scene,
            @PathVariable String conversationId,
            @RequestParam String visitorId)
    {
        publicAiChatService.deleteConversation(scene, conversationId, visitorId);
        return AjaxResult.success();
    }

    @PostMapping("/{scene}/conversations/{conversationId}/messages")
    public AjaxResult sendMessage(@PathVariable String scene,
            @PathVariable String conversationId,
            @RequestBody AiChatSendRequest request)
    {
        return AjaxResult.success(publicAiChatService.sendMessage(scene, conversationId, request));
    }
}
