package com.ruoyi.knowledge.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.knowledge.domain.CustomerServiceTicket;
import com.ruoyi.knowledge.domain.CustomerServiceTicketHandleRequest;
import com.ruoyi.knowledge.mapper.CustomerServiceTicketMapper;

/**
 * 智能客服复杂问题工单的创建与人工处理。
 */
@Service
public class CustomerServiceTicketService
{
    private static final DateTimeFormatter TICKET_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final Set<String> PRIORITIES = Set.of("low", "medium", "high");

    private static final Set<String> ACTIONS = Set.of("approve", "modify", "escalate");

    private final CustomerServiceTicketMapper ticketMapper;

    public CustomerServiceTicketService(CustomerServiceTicketMapper ticketMapper)
    {
        this.ticketMapper = ticketMapper;
    }

    @Transactional
    public CustomerServiceTicket createComplexTicket(String conversationId, String visitorId,
            String issueType, String question, String category, String reason, String priority,
            String preanalysisSummary, String preanalysisSolution, String suggestedReply)
    {
        CustomerServiceTicket ticket = new CustomerServiceTicket();
        ticket.setTicketNo(nextTicketNo());
        ticket.setConversationId(conversationId);
        ticket.setVisitorId(visitorId);
        ticket.setIssueType(issueType);
        ticket.setQuestion(question);
        ticket.setCategory(category);
        ticket.setClassificationReason(reason);
        ticket.setPriority(normalizePriority(priority, "medium"));
        ticket.setStatus("WAITING");
        ticket.setPreanalysisSummary(preanalysisSummary);
        ticket.setPreanalysisSolution(preanalysisSolution);
        ticket.setSuggestedReply(suggestedReply);
        ticketMapper.insertTicket(ticket);
        return ticketMapper.selectTicketById(ticket.getTicketId());
    }

    public List<CustomerServiceTicket> listTickets(CustomerServiceTicket query)
    {
        return ticketMapper.selectTicketList(query == null ? new CustomerServiceTicket() : query);
    }

    @Transactional
    public CustomerServiceTicket handleTicket(Long ticketId, CustomerServiceTicketHandleRequest request,
            String username)
    {
        if (request == null)
        {
            throw new ServiceException("工单处理内容不能为空");
        }
        String action = normalizeAction(request.action());
        CustomerServiceTicket ticket = requireTicket(ticketId);
        String priority = normalizePriority(request.priority(), ticket.getPriority());
        String reply = StringUtils.trim(request.replyContent());
        String note = trim(StringUtils.trim(request.note()), 1000);

        ticket.setPriority(priority);
        ticket.setHandleNote(note);
        ticket.setHandledBy(username);
        ticket.setHandleTime(new Date());
        switch (action)
        {
            case "approve" -> {
                if (StringUtils.isBlank(ticket.getSuggestedReply()))
                {
                    throw new ServiceException("AI 建议回复为空，请选择修改后批准");
                }
                ticket.setHandlerReply(ticket.getSuggestedReply());
                ticket.setStatus("APPROVED");
            }
            case "modify" -> {
                if (StringUtils.isBlank(reply))
                {
                    throw new ServiceException("修改后批准必须填写回复内容");
                }
                ticket.setHandlerReply(reply);
                ticket.setStatus("MODIFIED");
            }
            case "escalate" -> {
                ticket.setHandlerReply(reply);
                ticket.setStatus("ESCALATED");
            }
            default -> throw new ServiceException("不支持的工单处理动作");
        }
        ticketMapper.updateTicket(ticket);
        return requireTicket(ticketId);
    }

    private CustomerServiceTicket requireTicket(Long ticketId)
    {
        if (ticketId == null)
        {
            throw new ServiceException("工单ID不能为空");
        }
        CustomerServiceTicket ticket = ticketMapper.selectTicketById(ticketId);
        if (ticket == null)
        {
            throw new ServiceException("工单不存在");
        }
        return ticket;
    }

    private String normalizeAction(String action)
    {
        String value = StringUtils.trim(action).toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(value))
        {
            throw new ServiceException("不支持的工单处理动作");
        }
        return value;
    }

    private String normalizePriority(String priority, String fallback)
    {
        String value = StringUtils.isBlank(priority) ? fallback : priority.trim().toLowerCase(Locale.ROOT);
        if (StringUtils.isBlank(value))
        {
            value = "medium";
        }
        if (!PRIORITIES.contains(value))
        {
            throw new ServiceException("优先级不正确");
        }
        return value;
    }

    private String nextTicketNo()
    {
        int suffix = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return "CS" + LocalDateTime.now().format(TICKET_TIME_FORMAT) + suffix;
    }

    private String trim(String value, int maxLength)
    {
        String result = value == null ? "" : value;
        return result.length() > maxLength ? result.substring(0, maxLength) : result;
    }
}
