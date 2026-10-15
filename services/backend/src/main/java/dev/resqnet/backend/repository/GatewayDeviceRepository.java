package dev.resqnet.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dev.resqnet.backend.domain.GatewayDevice;

/** Persistence for known gateway devices. */
public interface GatewayDeviceRepository extends JpaRepository<GatewayDevice, Long> {

    Optional<GatewayDevice> findByDeviceId(String deviceId);

    List<GatewayDevice> findAllByOrderByLastSeenAtDesc();

    /** Devices that have not reported since the cutoff. */
    @Query("select g from GatewayDevice g where g.lastReportedAt is null "
            + "or g.lastReportedAt < :cutoff order by g.lastSeenAt desc")
    List<GatewayDevice> findStale(Instant cutoff);
}
