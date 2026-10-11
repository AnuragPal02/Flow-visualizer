package com.example.service;

import com.example.model.Order;
import org.springframework.stereotype.Component;

@Component
public class PaymentAudit {
    public void record(Order order) {
        System.out.println("audit " + order);
    }
}
