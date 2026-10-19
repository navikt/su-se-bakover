package no.nav.su.se.bakover.client.historisk

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.domain.client.ClientError

/**
 * Stub for lokal kjøring og tester mot [SupstonadHistoriskClient].
 * Returnerer tomme/sikre svar som standard, men kan konfigureres for tester.
 */
class SupstonadHistoriskClientStub(
    private val tabeller: Map<String, List<String>> = emptyMap(),
    private val antall: Map<String, Long> = emptyMap(),
    private val uttrekk: MutableMap<String, ArrayDeque<UttrekkResponse>> = mutableMapOf(),
) : SupstonadHistoriskClient {
    override fun tellRader(tabellnavn: String): Either<ClientError, CountResponse> =
        CountResponse(antall.getOrDefault(tabellnavn, 0)).right()

    override fun hentUttrekk(
        tabellnavn: String,
        antallRader: Long,
        iterator: String?,
    ): Either<ClientError, UttrekkResponse> =
        (uttrekk[tabellnavn]?.removeFirst() ?: UttrekkResponse(iterator = "", schema = SchemaDto(kolonner = emptyList()), innhold = emptyList())).right()

    override fun hentTabeller(): Either<ClientError, Map<String, List<String>>> = tabeller.right()
}
