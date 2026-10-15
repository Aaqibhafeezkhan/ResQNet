package dev.resqnet.mesh.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DuplicateCache")
class DuplicateCacheTest {

    private final DuplicateCache cache = new DuplicateCache(4, 1_000L, 1_000);

    @Test
    @DisplayName("reports the first sighting and suppresses the rest")
    void suppressesDuplicates() {
        assertThat(cache.markIfFirstSeen("m1", 0L, 10)).isTrue();
        assertThat(cache.markIfFirstSeen("m1", 1L, 10)).isFalse();
        assertThat(cache.markIfFirstSeen("m1", 2L, 10)).isFalse();
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("treats distinct ids as distinct")
    void keepsDistinctIds() {
        assertThat(cache.markIfFirstSeen("m1", 0L, 10)).isTrue();
        assertThat(cache.markIfFirstSeen("m2", 0L, 10)).isTrue();
        assertThat(cache.markIfFirstSeen("m3", 0L, 10)).isTrue();
        assertThat(cache.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("expires entries once they are older than the retention window")
    void expiresOldEntries() {
        cache.markIfFirstSeen("m1", 0L, 10);

        assertThat(cache.isSeen("m1", 500L)).isTrue();
        assertThat(cache.isSeen("m1", 1_500L))
                .as("past the retention window the id must stop counting as seen")
                .isFalse();
    }

    @Test
    @DisplayName("re-admits an id after its retention window has passed")
    void readmitsAfterExpiry() {
        cache.markIfFirstSeen("m1", 0L, 10);
        assertThat(cache.markIfFirstSeen("m1", 5_000L, 10)).isTrue();
    }

    @Test
    @DisplayName("evicts the oldest entry when the entry ceiling is reached")
    void evictsByCount() {
        cache.markIfFirstSeen("a", 0L, 10);
        cache.markIfFirstSeen("b", 1L, 10);
        cache.markIfFirstSeen("c", 2L, 10);
        cache.markIfFirstSeen("d", 3L, 10);
        cache.markIfFirstSeen("e", 4L, 10);

        assertThat(cache.size()).isEqualTo(4);
        assertThat(cache.isSeen("a", 4L)).isFalse();
        assertThat(cache.isSeen("e", 4L)).isTrue();
    }

    @Test
    @DisplayName("evicts when the byte budget is exceeded")
    void evictsByBytes() {
        DuplicateCache small = new DuplicateCache(1000, 10_000L, 100);

        small.markIfFirstSeen("a", 0L, 60);
        small.markIfFirstSeen("b", 0L, 60);

        assertThat(small.bytesUsed()).isLessThanOrEqualTo(100);
        assertThat(small.isSeen("a", 0L)).isFalse();
        assertThat(small.isSeen("b", 0L)).isTrue();
    }

    @Test
    @DisplayName("forgets a single id on demand")
    void forgetsOnDemand() {
        cache.markIfFirstSeen("m1", 0L, 10);

        assertThat(cache.forget("m1")).isTrue();
        assertThat(cache.forget("m1")).isFalse();
        assertThat(cache.markIfFirstSeen("m1", 0L, 10)).isTrue();
    }

    @Test
    @DisplayName("never lets the byte counter drift negative")
    void accountingStaysConsistent() {
        cache.markIfFirstSeen("a", 0L, 100);
        cache.forget("a");
        cache.markIfFirstSeen("b", 0L, 50);

        assertThat(cache.bytesUsed()).isEqualTo(50);
    }

    @Test
    @DisplayName("rejects nonsensical bounds")
    void rejectsBadBounds() {
        assertThatThrownBy(() -> new DuplicateCache(0, 1_000L, 1_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DuplicateCache(10, 0L, 1_000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("survives a burst far larger than the cache")
    void survivesBurst() {
        for (int i = 0; i < 100_000; i++) {
            cache.markIfFirstSeen("m" + i, i, 16);
        }

        assertThat(cache.size()).isLessThanOrEqualTo(4);
        assertThat(cache.bytesUsed()).isLessThanOrEqualTo(1_000);
    }
}