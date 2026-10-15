package dev.resqnet.android.ui;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import java.util.List;

/**
 * Best-effort last known location.
 *
 * <p>Deliberately does not request a fresh fix. A fresh GPS fix takes tens of
 * seconds, which is an unacceptable delay for someone composing an SOS, and the
 * coarse network provider is usually available immediately.
 *
 * <p>Returns {@code {0, 0}} when no fix is available, which
 * {@link dev.resqnet.android.model.EmergencyPayload#inRange} treats as
 * "no location". Reporting no location is correct; reporting a fabricated one
 * would send a responder to the wrong place.
 */
final class LastKnownFixProvider {

    private LastKnownFixProvider() {
    }

    static double[] get(Context context) {
        LocationManager manager = (LocationManager) context.getSystemService(
                Context.LOCATION_SERVICE);
        if (manager == null) {
            return new double[] { 0, 0 };
        }
        Location best = null;
        try {
            List<String> providers = manager.getProviders(true);
            for (String provider : providers) {
                Location candidate = manager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) {
                    best = candidate;
                }
            }
        } catch (SecurityException e) {
            return new double[] { 0, 0 };
        }
        if (best == null) {
            return new double[] { 0, 0 };
        }
        return new double[] { best.getLatitude(), best.getLongitude() };
    }
}
