package com.example.service;

import com.example.model.Order;
import com.example.model.OrderDto;
import java.util.List;

public interface OrderService {
    Order getOrder(Long id);
    Order create(OrderDto dto);
    List<Order> listByStatus(String status);
}
