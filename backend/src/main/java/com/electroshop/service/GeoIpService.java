package com.electroshop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Best-effort IP geolocation using the free ip-api.com service. Never throws:
 * on any failure (timeout, rate limit, private/local IP) it returns an empty
 * result so a login is never blocked by geolocation.
 */
@Service
public class GeoIpService {

    private static final Logger log = LoggerFactory.getLogger(GeoIpService.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public record GeoInfo(String country, String city) {
        public static GeoInfo empty() { return new GeoInfo(null, null); }
    }

    public GeoInfo lookup(String ip) {
        if (ip == null || ip.isBlank() || isLocalOrPrivate(ip)) {
            return GeoInfo.empty();
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/json/" + ip + "?fields=status,country,city"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return GeoInfo.empty();
            JsonNode json = mapper.readTree(res.body());
            if (!"success".equals(json.path("status").asText())) return GeoInfo.empty();
            String country = emptyToNull(json.path("country").asText(null));
            String city = emptyToNull(json.path("city").asText(null));
            return new GeoInfo(country, city);
        } catch (Exception e) {
            log.debug("GeoIP lookup failed for {}: {}", ip, e.getMessage());
            return GeoInfo.empty();
        }
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private boolean isLocalOrPrivate(String ip) {
        return ip.equals("127.0.0.1") || ip.equals("::1") || ip.startsWith("0:0:0:0")
                || ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.startsWith("172.16.") || ip.startsWith("172.17.")
                || ip.startsWith("172.18.") || ip.startsWith("172.19.")
                || ip.startsWith("172.2") || ip.startsWith("172.30.") || ip.startsWith("172.31.")
                || ip.equalsIgnoreCase("localhost");
    }
}
