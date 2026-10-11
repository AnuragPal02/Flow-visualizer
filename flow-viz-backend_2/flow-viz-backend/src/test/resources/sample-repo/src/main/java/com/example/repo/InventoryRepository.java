package com.example.repo;

import com.example.model.Inventory;
import org.springframework.data.repository.CrudRepository;

public interface InventoryRepository extends CrudRepository<Inventory, Long> {
    int countBySku(String sku);
}
