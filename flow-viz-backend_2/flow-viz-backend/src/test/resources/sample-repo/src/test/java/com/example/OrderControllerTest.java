package com.example;

public class OrderControllerTest {
    // lives under src/test/java: must never be analyzed when the repo root is given
    void shouldNotBeAnalyzed() { new com.example.web.OrderController(null).list("x"); }
}
