package com.example.model;

public class Order {
    private String sku;
    private String status;
    private String region;
    private double total;

    public Order(String sku) { this.sku = sku; }
    public String getSku() { return sku; }
    public String getStatus() { return status; }
    public String getRegion() { return region; }
    public void setTotal(double total) { this.total = total; }
}
