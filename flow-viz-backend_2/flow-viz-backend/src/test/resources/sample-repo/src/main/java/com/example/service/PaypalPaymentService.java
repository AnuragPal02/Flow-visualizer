package com.example.service;

import com.example.model.Order;
import org.springframework.stereotype.Service;

@Service
public class PaypalPaymentService implements PaymentService {

    @Override
    public boolean validatePayment(Order order) {
        return true;
    }
}
