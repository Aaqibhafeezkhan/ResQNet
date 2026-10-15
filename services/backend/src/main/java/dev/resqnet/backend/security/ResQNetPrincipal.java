package dev.resqnet.backend.security;

import java.util.Optional;

import dev.resqnet.backend.domain.ResponderRole;

/**
 * The authenticated caller.
 *
 * <p>A principal is either a human responder or a gateway device. They are kept
 * as distinct types on purpose: a gateway may synchronise, a responder may act,
 * and conflating them is how a compromised device ends up with the authority to
 * close an incident.
 */
public record ResQNetPrincipal(String name, ResponderRole role, Long accountId, boolean gateway) {

    public static ResQNetPrincipal responder(String name, ResponderRole role, Long accountId) {
        return new ResQNetPrincipal(name, role, accountId, false);
    }

    /**
     * A gateway identity. It carries no responder role at all; the role field is
     * {@code null} and every authorisation check treats it as incapable of
     * changing incident state.
     */
    public static ResQNetPrincipal gateway(String deviceId) {
        return new ResQNetPrincipal("gateway:" + deviceId, null, null, true);
    }

    public boolean canReadIncidents() {
        return gateway() || role != null;
    }

    public boolean canMutateIncidents() {
        return !gateway() && role != null && role.canMutateIncidents();
    }

    public boolean canReadAudit() {
        return !gateway() && role != null && role.canReadAudit();
    }

    public Optional<ResponderRole> roleIfPresent() {
        return Optional.ofNullable(role);
    }
}
