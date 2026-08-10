package com.electroshop.repository;

import com.electroshop.model.Purchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Page<Purchase> findBySupplierId(Long supplierId, Pageable pageable);

    List<Purchase> findByPurchaseDateBetween(LocalDate from, LocalDate to);

    boolean existsBySupplierId(Long supplierId);

    /**
     * Recepția produsă dintr-un anumit fișier, dacă există.
     *
     * <p>Prima linie de apărare împotriva dublului import — accidentul cel mai
     * frecvent la intrările de marfă și singurul care nu produce nicio eroare:
     * cineva încarcă același fișier a doua oară și stocul se dublează tăcut.
     * Verificarea de aici dă un mesaj util, care trimite la documentul deja
     * existent; constrângerea de unicitate din tabelă este cea care garantează
     * rezultatul chiar și la două cereri simultane.</p>
     */
    Optional<Purchase> findBySourceFileHash(String sourceFileHash);

    /**
     * Cel mai mare număr de NIR folosit în serie.
     *
     * <p>Nu este sursa numerotării — aceea rămâne contorul din setările firmei —
     * ci corecția care detectează un contor rămas în urmă față de documentele
     * existente, exact ca la facturi.</p>
     */
    @Query("SELECT MAX(p.receptionNumber) FROM Purchase p WHERE p.receptionSeries = :series")
    Integer maxReceptionNumber(@Param("series") String series);
}
