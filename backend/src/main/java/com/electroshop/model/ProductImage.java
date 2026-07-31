package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A single hosted image belonging to a {@link Product}. Images live on
 * Cloudinary; {@code url} is the delivered (secure) URL and {@code publicId} is
 * the Cloudinary handle needed to delete the asset. Exactly one image per
 * product is flagged {@code primary} — that one is mirrored onto
 * {@link Product#getImageUrl()} so product cards keep working unchanged.
 */
@Entity
@Table(name = "product_images")
@Getter
@Setter
@NoArgsConstructor
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 500)
    private String url;

    /** Cloudinary public_id — required to remove the asset from Cloudinary. */
    @Column(length = 200)
    private String publicId;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Column(nullable = false)
    private int position = 0;

    /** Pixel width/height as reported by Cloudinary at upload time. Null for images uploaded before this field existed. */
    private Integer width;

    private Integer height;

    /** File format reported by Cloudinary, e.g. "jpg", "png", "webp". Null for pre-existing images. */
    @Column(length = 20)
    private String format;

    /** Original file size in bytes, as reported by Cloudinary. Null for pre-existing images. */
    private Long bytes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ProductImage(Product product, String url, String publicId, int position) {
        this.product = product;
        this.url = url;
        this.publicId = publicId;
        this.position = position;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
