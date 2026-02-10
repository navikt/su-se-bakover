package no.nav.su.se.bakover.client.person

import arrow.core.Either
import arrow.core.right
import com.github.benmanes.caffeine.cache.Cache
import no.nav.su.se.bakover.client.cache.newCache
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import person.domain.KunneIkkeHentePerson
import java.time.Duration

/**
 * [FnrCacheKey] sørger for at rettighetene til brukerne blir ivaretatt, mens systembrukeren har tilgang til alt.
 *
 * @param personCache Brukes av både brukere og systembrukeren.
 * @param aktørIdCache Brukes av både brukere og systembrukeren.
 */
internal class PdlClientWithCache(
    private val pdlClient: PdlClient,
    suMetrics: SuMetrics,
    private val personCache: Cache<FnrCacheKey, PdlData> = newCache(
        cacheName = "person/domain",
        expireAfterWrite = Duration.ofMinutes(30), // aldri mer enn 60 min da tokenets levetid er 60 min maks
        suMetrics = suMetrics,
    ),
    private val aktørIdCache: Cache<FnrCacheKey, AktørId> = newCache(
        cacheName = "aktoerId",
        expireAfterWrite = Duration.ofMinutes(30),
        suMetrics = suMetrics,
    ),
) {
    private fun <Value : Any, Error : Any> Cache<FnrCacheKey, Value>.getOrAdd(
        key: FnrCacheKey,
        mappingFunction: () -> Either<Error, Value>,
    ): Either<Error, Value> {
        return this.getIfPresent(key)?.right() ?: mappingFunction().map { value ->
            this.put(key, value)
            if (key.second is JwtToken.BrukerToken) {
                // Dersom dette ble trigget av et brukertoken, ønsker vi å cache det for SystemToken også; men ikke andre veien.
                this.put(FnrCacheKey(key.first, JwtToken.SystemToken), value)
            }
            value
        }
    }

    fun person(fnr: Fnr, brukerToken: JwtToken.BrukerToken): Either<KunneIkkeHentePerson, PdlData> {
        return personCache.getOrAdd(Pair(fnr, brukerToken)) {
            pdlClient.person(fnr, brukerToken)
        }
    }

    fun personForSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, PdlData> {
        return personCache.getOrAdd(Pair(fnr, JwtToken.SystemToken)) {
            pdlClient.personForSystembruker(fnr)
        }
    }

    fun aktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
        return aktørIdCache.getOrAdd(Pair(fnr, JwtToken.SystemToken)) {
            pdlClient.aktørIdMedSystembruker(fnr)
        }
    }
}

internal typealias FnrCacheKey = Pair<Fnr, JwtToken>
