package com.example.repo;

import com.example.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStatus(String status);

    @Query("select o from Order o " + "where o.total > ?1")
    List<Order> findExpensive(double min);

    @Modifying
    @Query("delete from Order o where o.status = ?1")
    int deleteByStatus(String status);
}
