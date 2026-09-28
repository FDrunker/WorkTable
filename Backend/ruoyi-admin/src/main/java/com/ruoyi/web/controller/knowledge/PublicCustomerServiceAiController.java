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
import com.ruoyi.knowledge.domain.CustomerServiceSendRequest;
import com.ruoyi.knowledge.service.CustomerServiceAiChatService;

/**
 * 免登录智能客服工单接口。
 */
@Anonymous
@RestController
@RequestMapping("/public/customer-service-ai")
public class PublicCustomerServiceAiController extends BaseController
{
    private final CustomerServiceAiChatService customerServiceAiChatService;

    public PublicCustomerServiceAiController(CustomerServiceAiChatService customerServiceAiChatService)
    {
        this.customerServiceAiChatService = customerServiceAiChatService;
    }

    @GetMapping("/bootstrap")
    public AjaxResult bootstrap(@RequestParam(required = false) String visitorId)
    {
        return AjaxResult.success(customerServiceAiChatService.bootstrap(visitorId));
    }

    @PostMapping("/conversations")
    public AjaxResult createConversation(@RequestParam String visitorId)
    {
        return AjaxResult.success(customerServiceAiChatService.createConversation(visitorId));
    }

    @GetMapping("/conversations")
    public AjaxResult conversations(@RequestParam String visitorId)
    {
        return AjaxResult.success(customerServiceAiChatService.listConversations(visitorId));
    }

    @GetMapping("/conversations/{conversationId}")
    public AjaxResult conversation(@PathVariable String conversationId, @RequestParam String visitorId)
    {
        return AjaxResult.success(customerServiceAiChatService.getConversation(conversationId, visitorId));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public AjaxResult deleteConversation(@PathVariable String conversationId, @RequestParam String visitorId)
    {
        customerServiceAiChatService.deleteConversation(conversationId, visitorId);
        return AjaxResult.success();
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public AjaxResult sendMessage(@PathVariable String conversationId,
            @RequestBody CustomerServiceSendRequest request)
    {
        return AjaxResult.success(customerServiceAiChatService.sendMessage(conversationId, request));
    }
}
