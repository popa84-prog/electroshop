package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One record per successful login. Gives the admin a "who connected, from where
 * and when" audit trail (feature: monitorizare conectări).
 */
@Entity
@Table(name = "login_events", indexes = {
        @Index(name = "idx_login_events_user", columnList = "userId"),
        @Index(name = "idx_login_events_time", columnList = "loginAt")
})
@Getter
@Setter
@NoArgsConstructor
public class LoginEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(length = 150)
    private String userEmail;

    @Column(length = 120)
    private String userName;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 80)
    private String country;

    @Column(length = 120)
    private String city;

    @Column(length = 400)
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime loginAt = LocalDateTime.now();
}
