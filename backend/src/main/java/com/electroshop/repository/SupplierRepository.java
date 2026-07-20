package com.electroshop.repository;

import com.electroshop.model.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Page<Supplier> findByNameContainingIgnoreCaseOrContactNameContainingIgnoreCase(
            String name, String contactName, Pageable pageable);
}
