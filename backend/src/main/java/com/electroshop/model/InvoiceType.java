package com.electroshop.model;

/**
 * Ce fel de document fiscal este rândul din {@code invoices}.
 *
 * <p>Ambele tipuri iau număr din același contor, pentru că aceasta este seria
 * unică aleasă pentru magazin. Tipul distinge documentele între ele; nu
 * influențează numerotarea.</p>
 */
public enum InvoiceType {

    /**
     * Factură obișnuită. Cantități și valori pozitive.
     */
    INVOICE,

    /**
     * Factură de stornare. Cantitățile și valorile sunt negative, iar
     * {@code originalInvoice} arată documentul pe care îl corectează.
     *
     * <p>Un storno nu se stornează la rândul lui: dacă a fost emis din greșeală,
     * se emite o factură nouă pentru ce a rămas de facturat. Altfel s-ar ajunge
     * la lanțuri de corecții în care soldul real al unei comenzi nu mai poate fi
     * citit fără să parcurgi tot istoricul.</p>
     */
    STORNO
}
