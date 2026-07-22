package com.electroshop.dto;

import com.electroshop.model.LoginEvent;

import java.time.LocalDateTime;

public record LoginEventDto(
        Long id,
        Long userId,
        String userEmail,
        String userName,
        String ipAddress,
        String country,
        String city,
        String location,
        String userAgent,
        LocalDateTime loginAt
) {
    public static LoginEventDto from(LoginEvent e) {
        String loc;
        if (e.getCity() != null && e.getCountry() != null) loc = e.getCity() + ", " + e.getCountry();
        else if (e.getCountry() != null) loc = e.getCountry();
        else if (e.getCity() != null) loc = e.getCity();
        else loc = "Necunoscut";
        return new LoginEventDto(
                e.getId(), e.getUserId(), e.getUserEmail(), e.getUserName(),
                e.getIpAddress(), e.getCountry(), e.getCity(), loc,
                e.getUserAgent(), e.getLoginAt());
    }
}
