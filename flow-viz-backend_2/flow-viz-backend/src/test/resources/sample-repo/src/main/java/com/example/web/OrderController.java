package com.example.web;

import com.example.model.Order;
import com.example.model.OrderDto;
import com.example.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Returns one order together with its price. */
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @GetMapping
    public List<Order> list(@RequestParam String status) {
        return orderService.listByStatus(status);
    }

    @PostMapping
    public Order create(@RequestBody OrderDto dto) {
        return orderService.create(dto);
    }
}
