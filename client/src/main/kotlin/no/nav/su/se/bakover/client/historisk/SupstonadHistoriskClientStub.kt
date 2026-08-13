package no.nav.su.se.bakover.client.historisk

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.domain.client.ClientError

/**
 * Stub for lokal kjøring og tester mot [SupstonadHistoriskClient].
 * Returnerer tomme/sikre svar slik at appen starter uten tilgang til supstonad-historisk.
 */
class SupstonadHistoriskClientStub : SupstonadHistoriskClient {
    override fun tellRader(tabellnavn: String): Either<ClientError, CountResponse> {
        return CountResponse(antall = 0).right()
    }

    override fun hentUttrekk(
        tabellnavn: String,
        antallRader: Long,
        iterator: String?,
    ): Either<ClientError, UttrekkResponse> {
        return UttrekkResponse(
            iterator = "",
            schema = SchemaDto(kolonner = emptyList()),
            innhold = emptyList(),
        ).right()
    }

    override fun hentTabeller(): Either<ClientError, Map<String, List<String>>> {
        return emptyMap<String, List<String>>().right()
    }
}
