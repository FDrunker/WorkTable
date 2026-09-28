package com.ruoyi.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.CustomerServiceTicket;
import com.ruoyi.knowledge.domain.CustomerServiceTicketHandleRequest;
import com.ruoyi.knowledge.mapper.CustomerServiceTicketMapper;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTicketServiceTest
{
    @Mock
    private CustomerServiceTicketMapper ticketMapper;

    private CustomerServiceTicketService service;

    @BeforeEach
    void setUp()
    {
        service = new CustomerServiceTicketService(ticketMapper);
    }

    @Test
    void approveUsesAiSuggestedReply()
    {
        CustomerServiceTicket ticket = ticket();
        when(ticketMapper.selectTicketById(7L)).thenReturn(ticket);

        CustomerServiceTicket result = service.handleTicket(
                7L,
                new CustomerServiceTicketHandleRequest("approve", "high", "ignored", "确认批准"),
                "agent");

        assertEquals("APPROVED", result.getStatus());
        assertEquals("建议回复", result.getHandlerReply());
        assertEquals("high", result.getPriority());
        verify(ticketMapper).updateTicket(ticket);
    }

    @Test
    void modifyRequiresAndStoresHumanReply()
    {
        CustomerServiceTicket ticket = ticket();
        when(ticketMapper.selectTicketById(7L)).thenReturn(ticket);

        assertThrows(ServiceException.class, () -> service.handleTicket(
                7L,
                new CustomerServiceTicketHandleRequest("modify", "medium", "", ""),
                "agent"));

        CustomerServiceTicket result = service.handleTicket(
                7L,
                new CustomerServiceTicketHandleRequest("modify", "medium", "人工修改后的回复", "已核对"),
                "agent");

        assertEquals("MODIFIED", result.getStatus());
        assertEquals("人工修改后的回复", result.getHandlerReply());
    }

    @Test
    void escalateMovesTicketToSpecialistTeam()
    {
        CustomerServiceTicket ticket = ticket();
        when(ticketMapper.selectTicketById(7L)).thenReturn(ticket);

        CustomerServiceTicket result = service.handleTicket(
                7L,
                new CustomerServiceTicketHandleRequest("escalate", "high", "", "转专项团队"),
                "agent");

        assertEquals("ESCALATED", result.getStatus());
        assertEquals("转专项团队", result.getHandleNote());
    }

    private CustomerServiceTicket ticket()
    {
        CustomerServiceTicket ticket = new CustomerServiceTicket();
        ticket.setTicketId(7L);
        ticket.setTicketNo("CS20260928120000123");
        ticket.setPriority("medium");
        ticket.setStatus("WAITING");
        ticket.setSuggestedReply("建议回复");
        return ticket;
    }
}
