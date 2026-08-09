package com.electroshop.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Favourite routes and note links are user-controlled data that the interface
 * turns into anchors, which makes them the one place in this feature where a
 * stored value becomes a navigation target.
 *
 * <p>A route that escaped this check would be a stored redirect carrying the
 * operator's session — the shape of attack that a "harmless" preferences table
 * is exactly the right hiding place for. These cases are the ones a
 * starts-with-a-slash check misses.</p>
 */
class AdminPreferenceRouteSafetyTest {

    @Test
    void ordinaryAdminRoutesAreAccepted() {
        assertTrue(AdminPreferenceService.isSafeAdminRoute("/admin/products"));
        assertTrue(AdminPreferenceService.isSafeAdminRoute("/admin/orders?id=42"));
        assertTrue(AdminPreferenceService.isSafeAdminRoute("/admin/login-events"));
    }

    @Test
    void aProtocolRelativeUrlIsRejectedDespiteStartingWithASlash() {
        // "//evil.example" is a complete off-site URL. It passes a naive
        // "startsWith('/')" check and navigates away from the application, which
        // is precisely why the leading-slash test alone is not enough.
        assertFalse(AdminPreferenceService.isSafeAdminRoute("//evil.example/admin/products"));
    }

    @Test
    void anAbsoluteUrlIsRejectedEvenWhenItMentionsAdmin() {
        assertFalse(AdminPreferenceService.isSafeAdminRoute("https://evil.example/admin/products"));
        assertFalse(AdminPreferenceService.isSafeAdminRoute("http://evil.example/admin/"));
    }

    @Test
    void aScriptSchemeIsRejected() {
        assertFalse(AdminPreferenceService.isSafeAdminRoute("javascript:alert(1)"));
        // Including the case where it hides behind a valid-looking prefix.
        assertFalse(AdminPreferenceService.isSafeAdminRoute("/admin/x?next=javascript:alert(1)"));
    }

    @Test
    void aBackslashIsRejected() {
        // Several browsers normalise a backslash to a forward slash in URLs, so
        // "/admin\\..\\..\\evil" can escape the prefix after normalisation.
        assertFalse(AdminPreferenceService.isSafeAdminRoute("/admin\\evil"));
    }

    @Test
    void controlCharactersAreRejected() {
        // A newline inside a stored value is how header and attribute injection
        // starts; there is no legitimate route that contains one.
        assertFalse(AdminPreferenceService.isSafeAdminRoute("/admin/products\nSet-Cookie: x=1"));
        assertFalse(AdminPreferenceService.isSafeAdminRoute("/admin/products\rmore"));
    }

    @Test
    void routesOutsideTheAdminAreaAreRejected() {
        // Not an attack, but not a favourite either: the rail navigates within
        // the admin panel, and a storefront route would take the operator out of
        // it with no way back except the browser's own history.
        assertFalse(AdminPreferenceService.isSafeAdminRoute("/products"));
        assertFalse(AdminPreferenceService.isSafeAdminRoute("/"));
        assertFalse(AdminPreferenceService.isSafeAdminRoute("/adminproducts"));
    }

    @Test
    void nullAndOverlongValuesAreRejected() {
        assertFalse(AdminPreferenceService.isSafeAdminRoute(null));
        assertFalse(AdminPreferenceService.isSafeAdminRoute("/admin/" + "x".repeat(300)));
    }
}
