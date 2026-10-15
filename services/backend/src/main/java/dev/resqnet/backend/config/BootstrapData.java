package dev.resqnet.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import dev.resqnet.backend.domain.ResponderAccount;
import dev.resqnet.backend.domain.ResponderRole;
import dev.resqnet.backend.repository.ResponderAccountRepository;

/**
 * Creates a first responder account in the development profile.
 *
 * <p>Development only, and only when no accounts exist. A production database
 * gets its first account through a migration run by a human, because a
 * well-known default password in a deployed system is a way to lose an entire
 * incident queue.
 */
@Component
@Profile("dev")
public class BootstrapData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapData.class);

    private final ResponderAccountRepository accounts;
    private final PasswordEncoder encoder;

    public BootstrapData(ResponderAccountRepository accounts, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accounts.count() > 0) {
            return;
        }
        accounts.save(new ResponderAccount("responder", "Dev Responder",
                encoder.encode("resqnet-dev"), ResponderRole.RESPONDER));
        accounts.save(new ResponderAccount("admin", "Dev Admin",
                encoder.encode("resqnet-dev"), ResponderRole.ADMIN));
        log.warn("Development accounts created: responder/resqnet-dev and admin/resqnet-dev");
    }
}
