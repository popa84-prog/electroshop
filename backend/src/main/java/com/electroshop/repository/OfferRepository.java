package com.electroshop.repository;

import com.electroshop.model.Offer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    Page<Offer> findByTitleContainingIgnoreCaseOrHeadlineContainingIgnoreCase(
            String title, String headline, Pageable pageable);

    /**
     * Toate ofertele pornite dintr-o anumită zonă, în ordinea de afișare.
     * Filtrarea fină pe fereastra de timp se face în serviciu, prin
     * {@link Offer#isLiveAt}, ca regula să existe într-un singur loc și să nu
     * fie duplicată într-o interogare JPQL.
     */
    List<Offer> findByPlacementAndActiveTrueOrderBySortOrderAscIdAsc(Offer.Placement placement);

    boolean existsByTitleIgnoreCase(String title);
}
