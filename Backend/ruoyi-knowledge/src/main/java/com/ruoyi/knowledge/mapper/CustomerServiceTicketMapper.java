package com.ruoyi.knowledge.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.knowledge.domain.CustomerServiceTicket;

public interface CustomerServiceTicketMapper
{
    List<CustomerServiceTicket> selectTicketList(CustomerServiceTicket query);

    CustomerServiceTicket selectTicketById(@Param("ticketId") Long ticketId);

    CustomerServiceTicket selectTicketByNo(@Param("ticketNo") String ticketNo);

    int insertTicket(CustomerServiceTicket ticket);

    int updateTicket(CustomerServiceTicket ticket);
}
