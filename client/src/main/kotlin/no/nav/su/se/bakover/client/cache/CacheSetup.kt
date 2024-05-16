package no.nav.su.se.bakover.client.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import java.time.Duration

internal fun <K, V> newCache(
    maximumSize: Long = 500,
    expireAfterWrite: Duration = Duration.ofMinutes(1),
    cacheName: String,
    suMetrics: SuMetrics,
): Cache<K, V> {
    return Caffeine.newBuilder()
        .maximumSize(maximumSize)
        // Merk at det ikke er noen garanti for at Caffeine rydder opp selvom en verdi er expired, slik at cachen potensielt kan ta stor plass.
        // Les mer: https://github.com/ben-manes/caffeine/wiki/Cleanup
        .expireAfterWrite(expireAfterWrite)
        .recordStats()
        .build<K, V>()
        .also {
            suMetrics.monitorCache(it, cacheName)
        }
}
