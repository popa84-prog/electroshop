package com.electroshop.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Rezultatul unei operații în masă asupra comenzilor.
 *
 * <h2>De ce raportează și eșecurile, în loc să eșueze</h2>
 *
 * <p>O selecție de cincizeci de comenzi conține aproape sigur câteva care nu
 * pot primi acțiunea cerută: una este deja anulată, alteia i s-a emis deja
 * factura, alta a fost ștearsă între momentul selecției și cel al apăsării.
 * Dacă întregul lot ar eșua din cauza uneia, operatorul ar trebui să ghicească
 * pe care să o deselecteze și să reia — iar la a doua încercare ar descoperi
 * următoarea.</p>
 *
 * <p>Aici fiecare element este tratat separat, iar cele sărite sunt returnate
 * cu motivul. Operatorul vede exact ce s-a întâmplat și ce nu, dintr-o singură
 * apăsare.</p>
 *
 * <p><b>Excepția este validarea cererii însăși.</b> O listă goală, un status
 * inexistent sau o depășire a limitei de identificatori resping tot lotul,
 * pentru că acolo greșeala nu este a unei comenzi anume, ci a cererii.</p>
 *
 * @param requested      câte identificatori au fost trimiși
 * @param succeeded      câte au primit efectiv acțiunea
 * @param skipped        cele sărite, cu motivul fiecăreia
 * @param restockedUnits câte bucăți s-au întors în stoc, la anulare
 * @param message        rezumatul afișat operatorului
 */
public record OrderBulkResultDto(
        int requested,
        int succeeded,
        List<Skipped> skipped,
        int restockedUnits,
        String message
) {

    /**
     * O comandă care nu a primit acțiunea, și de ce.
     *
     * @param orderId comanda
     * @param reason  motivul, în limba operatorului
     */
    public record Skipped(Long orderId, String reason) {
    }

    public static Builder builder(int requested) {
        return new Builder(requested);
    }

    /**
     * Acumulator pentru parcurgerea lotului.
     */
    public static final class Builder {
        private final int requested;
        private int succeeded;
        private int restockedUnits;
        private final List<Skipped> skipped = new ArrayList<>();

        private Builder(int requested) {
            this.requested = requested;
        }

        public void ok() {
            succeeded++;
        }

        public void restocked(int units) {
            restockedUnits += units;
        }

        public void skip(Long orderId, String reason) {
            skipped.add(new Skipped(orderId, reason));
        }

        public OrderBulkResultDto build(String verb) {
            StringBuilder sb = new StringBuilder();
            sb.append(succeeded).append(" din ").append(requested).append(' ').append(verb);
            if (!skipped.isEmpty()) {
                sb.append(" · ").append(skipped.size()).append(" sărite");
            }
            if (restockedUnits > 0) {
                sb.append(" · ").append(restockedUnits).append(" buc. întoarse în stoc");
            }
            return new OrderBulkResultDto(requested, succeeded, skipped, restockedUnits, sb.toString());
        }
    }
}
