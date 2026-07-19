package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
    @Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
    @Getter
    @Setter
    @NoArgsConstructor
    public class User {

    @Id
            @GeneratedValue(strategy = GenerationType.IDENTITY)
            private Long id;

    @Column(nullable = false, length = 100)
            private String fullName;

    @Column(nullable = false, unique = true, length = 150)
            private String email;

    @Column(nullable = false)
            private String password;

    @Column(nullable = false)
            private boolean enabled = true;

    @Column(nullable = false, updatable = false)
            private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToMany(fetch = FetchType.EAGER)
            @JoinTable(
                            name = "user_roles",
                            joinColumns = @JoinColumn(name = "user_id"),
                            inverseJoinColumns = @JoinColumn(name = "role_id")
                    )
            private Set<Role> roles = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
            private Set<Order> orders = new HashSet<>();

    @PrePersist
            protected void onCreate() {
                        if (createdAt == null) {
                                        createdAt = LocalDateTime.now();
                        }
            }

    public void addRole(Role role) {
                this.roles.add(role);
    }
    }
