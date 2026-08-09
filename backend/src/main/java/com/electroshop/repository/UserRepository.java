package com.electroshop.repository;

import com.electroshop.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String fullName, String email, Pageable pageable);

    /** Accounts still waiting for admin approval (approved is false or null). */
    @Query("select u from User u where u.approved is null or u.approved = false")
    Page<User> findPending(Pageable pageable);

    @Query("select count(u) from User u where u.approved is null or u.approved = false")
    long countPending();

    /** New signups per calendar day — feeds the "Utilizatori" stat-card trend on the dashboard. */
    @Query(value = """
            SELECT DATE(created_at) AS d, COUNT(*)
            FROM users
            GROUP BY DATE(created_at)
            ORDER BY d
            """, nativeQuery = true)
    List<Object[]> countSignupsByDay();

    /**
     * Global search over email and full name.
     *
     * <p>The term is bound as a parameter and the wildcards are added by the query, so a
     * value containing a percent sign searches for that character rather than matching
     * every account.</p>
     *
     * <p>Enabled accounts come first: an operator searching a person almost always means
     * the account currently in use, and a disabled duplicate at the top of the list is a
     * plausible-looking wrong answer.</p>
     */
    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY u.enabled DESC, u.email ASC
            """)
    List<User> searchForGlobal(@org.springframework.data.repository.query.Param("q") String q,
                               Pageable pageable);
}
