package com.electroshop.repository;

import com.electroshop.model.LoginEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginEventRepository extends JpaRepository<LoginEvent, Long> {

    Page<LoginEvent> findAllByOrderByLoginAtDesc(Pageable pageable);

    Page<LoginEvent> findByUserIdOrderByLoginAtDesc(Long userId, Pageable pageable);
}
