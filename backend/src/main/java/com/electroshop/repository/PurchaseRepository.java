package com.electroshop.repository;

import com.electroshop.model.Purchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Page<Purchase> findBySupplierId(Long supplierId, Pageable pageable);

    List<Purchase> findByPurchaseDateBetween(LocalDate from, LocalDate to);

    boolean existsBySupplierId(Long supplierId);
}
