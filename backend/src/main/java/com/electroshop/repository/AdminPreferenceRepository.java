package com.electroshop.repository;

import com.electroshop.model.AdminPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Per-administrator settings storage.
 *
 * <p>Every method is scoped by {@code adminId}. There is deliberately no
 * {@code findByPrefKey}: a query that can return another administrator's preferences
 * is a query that will eventually be called without the owner check, and the safest
 * place to make that impossible is the repository interface.</p>
 */
public interface AdminPreferenceRepository extends JpaRepository<AdminPreference, Long> {

    Optional<AdminPreference> findByAdminIdAndPrefKey(Long adminId, String prefKey);

    /** Every setting owned by one administrator, for a single-request panel load. */
    List<AdminPreference> findByAdminId(Long adminId);

    /** Removes one setting, which is how "reset layout" is implemented. */
    void deleteByAdminIdAndPrefKey(Long adminId, String prefKey);

    /** Removes every setting of an administrator, used when the account is deleted. */
    void deleteByAdminId(Long adminId);
}
