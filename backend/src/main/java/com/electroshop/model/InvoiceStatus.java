package com.electroshop.model;

/**
 * Cât din factură mai este în vigoare.
 *
 * <p>Statutul se calculează din cantitățile stornate pe linii, nu se setează
 * manual. O factură ajunge {@code CANCELLED} doar când fiecare linie a fost
 * stornată integral, iar asta o poate spune doar suma pe linii — un indicator
 * boolean „anulată" pus de operator ar putea contrazice liniile.</p>
 */
public enum InvoiceStatus {

    /**
     * Emisă și neatinsă de nicio stornare.
     */
    ISSUED,

    /**
     * Stornată în parte: cel puțin o linie are cantitate stornată, dar nu toate
     * liniile sunt stornate integral. Restul facturii rămâne datorat.
     */
    PARTIALLY_STORNOED,

    /**
     * Stornată integral. Fiecare linie a fost creditată în întregime, deci
     * documentul nu mai produce niciun efect fiscal.
     *
     * <p>Rândul rămâne în baza de date cu numărul lui. O factură emisă nu se
     * șterge: numărul ei a fost raportat, iar dispariția lui ar lăsa o gaură în
     * serie pe care nimeni nu o mai poate explica.</p>
     */
    CANCELLED
}
