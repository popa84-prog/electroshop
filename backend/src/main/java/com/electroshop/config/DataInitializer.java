package com.electroshop.config;

import com.electroshop.model.*;
import com.electroshop.repository.ProductRepository;
import com.electroshop.repository.RoleRepository;
import com.electroshop.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Seeds roles, a default admin/user account and a few products the first time
 * the application starts, so the system is usable out of the box even when the
 * SQL seed script has not been run.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RoleRepository roleRepository, UserRepository userRepository,
                           ProductRepository productRepository, PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_USER)));
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_ADMIN)));

        if (userRepository.count() == 0) {
            User admin = new User();
            admin.setFullName("Administrator");
            admin.setEmail("admin@electroshop.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.addRole(adminRole);
            admin.addRole(userRole);
            userRepository.save(admin);

            User user = new User();
            user.setFullName("Demo User");
            user.setEmail("user@electroshop.com");
            user.setPassword(passwordEncoder.encode("user123"));
            user.addRole(userRole);
            userRepository.save(user);
        }

        if (productRepository.count() == 0) {
            seedProduct("iPhone 15 Pro", "Apple", "Smartphones",
                    "Apple A17 Pro chip, 6.1\" ProMotion display, titanium frame.",
                    new BigDecimal("5499.00"), 25,
                    "https://images.unsplash.com/photo-1592286927505-1def25115558?w=600");
            seedProduct("Samsung Galaxy S24 Ultra", "Samsung", "Smartphones",
                    "200MP camera, S-Pen, Snapdragon 8 Gen 3, 6.8\" AMOLED.",
                    new BigDecimal("5999.00"), 18,
                    "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?w=600");
            seedProduct("MacBook Air M3", "Apple", "Laptops",
                    "13.6\" Liquid Retina, Apple M3, 16GB RAM, 512GB SSD.",
                    new BigDecimal("7299.00"), 12,
                    "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600");
            seedProduct("Dell XPS 15", "Dell", "Laptops",
                    "Intel Core i7, RTX 4050, 16GB RAM, 1TB SSD, 15.6\" OLED.",
                    new BigDecimal("8499.00"), 9,
                    "https://images.unsplash.com/photo-1593642702821-c8da6771f0c6?w=600");
            seedProduct("Sony WH-1000XM5", "Sony", "Audio",
                    "Industry-leading noise cancelling wireless headphones.",
                    new BigDecimal("1699.00"), 40,
                    "https://images.unsplash.com/photo-1618366712010-f4ae9c647dcb?w=600");
        }
    }

    private void seedProduct(String name, String brand, String category, String description,
                             BigDecimal price, int stock, String imageUrl) {
        Product p = new Product();
        p.setName(name);
        p.setBrand(brand);
        p.setCategory(category);
        p.setDescription(description);
        p.setPrice(price);
        p.setStockQuantity(stock);
        p.setImageUrl(imageUrl);
        productRepository.save(p);
    }
}
