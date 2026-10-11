package com.example.service;

import com.example.model.Order;

public interface PaymentService {
    boolean validatePayment(Order order);
}
