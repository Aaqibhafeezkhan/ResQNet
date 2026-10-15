package dev.resqnet.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.resqnet.backend.domain.ResponderAccount;

/** Persistence for responder accounts. */
public interface ResponderAccountRepository extends JpaRepository<ResponderAccount, Long> {

    Optional<ResponderAccount> findByUsername(String username);

    boolean existsByUsername(String username);
}
