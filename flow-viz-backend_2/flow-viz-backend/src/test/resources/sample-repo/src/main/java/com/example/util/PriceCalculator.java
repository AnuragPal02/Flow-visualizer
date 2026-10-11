package com.example.util;

import com.example.model.Order;

public class PriceCalculator {
    public double total(Order order) {
        double base = Math.round(100.0);
        return base * TaxHelper.rate(order.getRegion());
    }
}
