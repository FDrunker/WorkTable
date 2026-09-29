package com.ruoyi.knowledge.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.domain.CustomerServiceTicket;
import com.ruoyi.knowledge.domain.CustomerServiceTicketHandleRequest;
import com.ruoyi.knowledge.service.CustomerServiceTicketService;

@RestController
@RequestMapping("/knowledge/customer-service/tickets")
public class CustomerServiceTicketController extends BaseController
{
    private final CustomerServiceTicketService ticketService;

    public CustomerServiceTicketController(CustomerServiceTicketService ticketService)
    {
        this.ticketService = ticketService;
    }

    @PreAuthorize("@ss.hasPermi('customer-service:ticket:list')")
    @GetMapping
    public AjaxResult list(CustomerServiceTicket query)
    {
        return AjaxResult.success(ticketService.listTickets(query));
    }

    @PreAuthorize("@ss.hasPermi('customer-service:ticket:edit')")
    @Log(title = "智能客服工单", businessType = BusinessType.UPDATE)
    @PostMapping("/{ticketId}/handle")
    public AjaxResult handle(@PathVariable Long ticketId,
            @RequestBody CustomerServiceTicketHandleRequest request)
    {
        return AjaxResult.success(ticketService.handleTicket(ticketId, request, getUsername()));
    }
}
