package com.example.service;

import com.example.model.Order;
import com.example.model.OrderDto;
import com.example.repo.OrderRepository;
import com.example.util.PriceCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PaymentService stripePaymentService;
    private final PaymentService paymentService;
    private final ShippingClient shippingClient;
    private final PriceCalculator priceCalculator = new PriceCalculator();

    public OrderServiceImpl(OrderRepository orderRepository, InventoryService inventoryService,
                            PaymentService stripePaymentService, PaymentService paymentService,
                            ShippingClient shippingClient) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.stripePaymentService = stripePaymentService;
        this.paymentService = paymentService;
        this.shippingClient = shippingClient;
    }

    /**
     * Loads an order and prices it. Fails when the stock check does not pass.
     */
    @Override
    @Transactional(readOnly = true)
    public Order getOrder(Long id) {
        log.info("Fetching order {}", id);
        Order order = orderRepository.findById(id).orElseThrow(() -> new IllegalStateException("missing"));
        inventoryService.check(order.getSku());
        stripePaymentService.validatePayment(order);
        String label = String.format("%s-%d", order.getStatus(), id);
        List<String> tags = new ArrayList<>();
        tags.add(label);
        return applyPricing(order);
    }

    @Override
    @Transactional
    public Order create(OrderDto dto) {
        Order order = new Order(dto.getSku());
        paymentService.validatePayment(order);
        shippingClient.reserve(order);
        return orderRepository.save(order);
    }

    @Override
    public List<Order> listByStatus(String status) {
        orderRepository.findExpensive(1000.0);
        return orderRepository.findByStatus(status).stream()
                .map(o -> applyPricing(o))
                .collect(Collectors.toList());
    }

    private Order applyPricing(Order order) {
        order.setTotal(priceCalculator.total(order));
        return order;
    }
}
