package com.electroshop.service;

import com.electroshop.dto.DeltaDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DeltaDto} decides what every trend badge on the dashboard says, and two
 * of its rules are ones a naive implementation always gets wrong: growth from
 * zero, and the difference between "went up" and "improved".
 */
class DeltaDtoTest {

    @Test
    void growthFromZeroHasNoPercentage() {
        // Revenue going from 0 to 5000 has not grown by any number of percent —
        // the denominator does not exist. +100% understates it, infinity is not
        // a number anyone reads, and 0% claims nothing happened. Null is the one
        // honest answer, and the badge renders it as "nou".
        DeltaDto delta = DeltaDto.higherIsBetter(BigDecimal.valueOf(5000), BigDecimal.ZERO);

        assertNull(delta.changePct());
        // Still an improvement: the business went from nothing to something.
        assertTrue(delta.improving());
    }

    @Test
    void anAbsentPreviousValueIsTreatedTheSameAsZero() {
        DeltaDto delta = DeltaDto.higherIsBetter(BigDecimal.TEN, null);

        assertNull(delta.changePct());
        assertEquals(BigDecimal.ZERO, delta.previous());
    }

    @Test
    void aRiseIsGoodNewsForRevenueAndBadNewsForReturns() {
        // The whole reason `improving` exists rather than the badge reading the
        // sign. A rise in revenue and a rise in the return rate are both positive
        // numbers, and colouring the second one green is the kind of confidently
        // wrong signal an operator acts on.
        BigDecimal before = BigDecimal.valueOf(100);
        BigDecimal after = BigDecimal.valueOf(120);

        assertTrue(DeltaDto.higherIsBetter(after, before).improving());
        assertFalse(DeltaDto.lowerIsBetter(after, before).improving());
    }

    @Test
    void aFallIsBadNewsForRevenueAndGoodNewsForDeliveryTime() {
        BigDecimal before = BigDecimal.valueOf(100);
        BigDecimal after = BigDecimal.valueOf(80);

        assertFalse(DeltaDto.higherIsBetter(after, before).improving());
        assertTrue(DeltaDto.lowerIsBetter(after, before).improving());
    }

    @Test
    void noChangeCountsAsNotWorse() {
        // A flat metric is not a regression under either direction. The badge
        // renders it as "neschimbat" rather than picking a colour.
        BigDecimal same = BigDecimal.valueOf(42);

        assertEquals(0.0, DeltaDto.higherIsBetter(same, same).changePct());
        assertTrue(DeltaDto.higherIsBetter(same, same).improving());
        assertTrue(DeltaDto.lowerIsBetter(same, same).improving());
    }

    @Test
    void thePercentageIsRoundedToOneDecimal() {
        // 100 -> 133 is 33%; 100 -> 133.33 is 33.3%. More decimals on a badge is
        // noise, fewer loses a real difference between two similar periods.
        assertEquals(33.0, DeltaDto.percentChange(
                BigDecimal.valueOf(133), BigDecimal.valueOf(100)));
        assertEquals(33.3, DeltaDto.percentChange(
                new BigDecimal("133.33"), BigDecimal.valueOf(100)));
    }

    @Test
    void aNegativeBaselineStillProducesAMeaningfulDirection() {
        // Potential profit can be negative when stock is priced below cost.
        // Dividing by a negative baseline without taking its magnitude would
        // flip the sign and report a recovery as a decline.
        Double pct = DeltaDto.percentChange(
                BigDecimal.valueOf(-50), BigDecimal.valueOf(-100));

        // Moving from -100 to -50 is an increase of 50 against a magnitude of
        // 100, so +50%.
        assertEquals(50.0, pct);
    }

    @Test
    void currentAndPreviousAreNeverNullInTheResponse() {
        // The frontend formats both values directly. Returning null for either
        // would make every consumer guard, and one of them would forget.
        DeltaDto delta = DeltaDto.higherIsBetter(null, null);

        assertEquals(BigDecimal.ZERO, delta.current());
        assertEquals(BigDecimal.ZERO, delta.previous());
        assertNull(delta.changePct());
    }
}
