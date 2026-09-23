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

    // ---- Proveniență ----
    //
    // Cine ne-a dat voie să folosim imaginea aceasta. Nu este birocrație: o
    // marcă își poate retrage permisiunea din Icecat oricând, iar atunci
    // trebuie să pot șterge exact pozele ei, cu o interogare, nu să caut prin
    // trei sute de rânduri. Coloanele există de la început tocmai pentru că
    // adăugarea lor după ingestie ar lăsa pozele deja urcate fără răspuns la
    // întrebarea „de unde vine asta".

    /**
     * Sursa: {@code OWN} (fotografie proprie), {@code ICECAT}, {@code SUPPLIER}
     * (feed de distribuitor) sau {@code PLACEHOLDER}. Null pentru imaginile
     * urcate înainte ca aceste coloane să existe — deliberat, ca să se vadă
     * care sunt și să poată fi completate.
     */
    @Column(name = "source", length = 30)
    private String source;

    /** Marca ale cărei drepturi acoperă imaginea, așa cum a fost interogată. */
    @Column(name = "source_brand", length = 80)
    private String sourceBrand;

    /** Identificatorul la sursă: id-ul Icecat, codul din feed, ce se aplică. */
    @Column(name = "source_ref", length = 120)
    private String sourceRef;

    /** Adresa originală, înainte de urcarea pe Cloudinary. */
    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    /**
     * Temeiul folosirii: nivelul Icecat ({@code openicecat} / {@code fullicecat})
     * sau referința înțelegerii cu distribuitorul.
     *
     * <p>Distincția dintre cele două niveluri Icecat contează juridic. La
     * nivelul deschis, marca a plătit ca pozele să ajungă la revânzători. La
     * nivelul complet, Icecat dă datele, nu drepturile asupra imaginilor.</p>
     */
    @Column(name = "licence_ref", length = 120)
    private String licenceRef;

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
