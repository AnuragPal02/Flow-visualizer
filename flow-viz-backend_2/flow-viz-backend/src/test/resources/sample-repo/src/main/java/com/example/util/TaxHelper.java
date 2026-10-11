package com.example.util;

public class TaxHelper {
    public static double rate(String region) {
        return "EU".equals(region) ? 1.2 : 1.0;
    }
}
