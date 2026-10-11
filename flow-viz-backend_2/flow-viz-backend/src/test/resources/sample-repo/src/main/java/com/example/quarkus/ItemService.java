package com.example.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ItemService {

    @Inject
    ItemRepository itemRepository;

    public Object find(Long id) {
        return itemRepository.findById(id);
    }
}
