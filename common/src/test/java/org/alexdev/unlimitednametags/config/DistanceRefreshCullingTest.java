package org.alexdev.unlimitednametags.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistanceRefreshCullingTest {

    @Test
    void intervalStaysBaseInsideNearDistance() {
        assertEquals(20, DistanceRefreshCulling.intervalTicks(20, 100, 24.0, 96.0, 2.0, 12.0));
        assertEquals(20, DistanceRefreshCulling.intervalTicks(20, 100, 24.0, 96.0, 2.0, 24.0));
    }

    @Test
    void intervalGrowsQuadraticallyUntilMaxDistance() {
        assertEquals(40, DistanceRefreshCulling.intervalTicks(20, 100, 24.0, 96.0, 2.0, 60.0));
        assertEquals(100, DistanceRefreshCulling.intervalTicks(20, 100, 24.0, 96.0, 2.0, 96.0));
        assertEquals(100, DistanceRefreshCulling.intervalTicks(20, 100, 24.0, 96.0, 2.0, 200.0));
    }

    @Test
    void disabledCullingAlwaysUsesBaseInterval() {
        Settings.DistanceRefreshCulling cfg = new Settings.DistanceRefreshCulling();
        cfg.setEnabled(false);
        cfg.setNearDistance(24.0);
        cfg.setMaxDistance(96.0);
        cfg.setMaxInterval(100);
        cfg.setCurve(2.0);

        assertEquals(20, DistanceRefreshCulling.intervalTicks(20, cfg, 200.0));
    }

    @Test
    void refreshWhenElapsedCatchesEffectiveInterval() {
        assertFalse(DistanceRefreshCulling.shouldRefresh(39, 40));
        assertTrue(DistanceRefreshCulling.shouldRefresh(40, 40));
        assertTrue(DistanceRefreshCulling.shouldRefresh(80, 40));
    }

    @Test
    void refreshesWhenElapsedIsNegativeAfterClockReset() {
        assertTrue(DistanceRefreshCulling.shouldRefresh(-80, 40));
    }
}
