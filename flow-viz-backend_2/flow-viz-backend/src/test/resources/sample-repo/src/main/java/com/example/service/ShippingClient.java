package com.example.service;

import com.example.model.Order;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "shipping-service", url = "${shipping.url}")
public interface ShippingClient {
    boolean reserve(Order order);
}
