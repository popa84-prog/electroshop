package com.electroshop.repository;

import com.electroshop.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String fullName, String email, Pageable pageable);

    /** Accounts still waiting for admin approval (approved is false or null). */
    @Query("select u from User u where u.approved is null or u.approved = false")
    Page<User> findPending(Pageable pageable);

    @Query("select count(u) from User u where u.approved is null or u.approved = false")
    long countPending();
}
