package no.nav.su.se.bakover.web.services.klage.klageinstans

import arrow.core.Either
import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.klage.KunneIkkeTolkeKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import org.slf4j.LoggerFactory
import java.util.UUID

data class AnkeITrygderettenbehandlingOpprettetDto(
    override val kildeReferanse: String,
    val detaljer: DetaljerWrapper,
) : KlageinstanshendelseDto {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun toDomain(
        id: UUID,
        opprettet: Tidspunkt,
    ): Either<KunneIkkeTolkeKlageinstanshendelse, TolketKlageinstanshendelse.AnkeITrygderettenOpprettet> {
        return Either.catch {
            TolketKlageinstanshendelse.AnkeITrygderettenOpprettet(
                id = id,
                opprettet = opprettet,
                klageId = KlageId(UUID.fromString(kildeReferanse)),
                sendtTilTrygderetten = parseKabalDatetime(detaljer.ankeITrygderettenbehandlingOpprettet.sendtTilTrygderetten),
                utfall = detaljer.ankeITrygderettenbehandlingOpprettet.utfall,
            )
        }.mapLeft {
            log.error("Kunne ikke tolke klageinstanshendelse.", it)
            KunneIkkeTolkeKlageinstanshendelse.UgyldigeVerdier
        }
    }

    data class DetaljerWrapper(
        val ankeITrygderettenbehandlingOpprettet: Detaljer,
    )

    data class Detaljer(
        val sendtTilTrygderetten: String,
        val utfall: String,
    )
}
