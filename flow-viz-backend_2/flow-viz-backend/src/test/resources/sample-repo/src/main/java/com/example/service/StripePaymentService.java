package com.example.service;

import com.example.model.Order;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class StripePaymentService implements PaymentService {

    private final PaymentAudit paymentAudit;
    private final RestTemplate restTemplate;

    public StripePaymentService(PaymentAudit paymentAudit, RestTemplate restTemplate) {
        this.paymentAudit = paymentAudit;
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean validatePayment(Order order) {
        paymentAudit.record(order);
        restTemplate.postForObject("https://pay.example/validate", order, String.class);
        return order != null;
    }
}
