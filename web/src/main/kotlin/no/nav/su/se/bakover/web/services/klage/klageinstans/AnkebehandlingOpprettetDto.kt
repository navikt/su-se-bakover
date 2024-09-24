package no.nav.su.se.bakover.web.services.klage.klageinstans

import arrow.core.Either
import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.klage.KunneIkkeTolkeKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import org.slf4j.LoggerFactory
import java.util.UUID

data class AnkebehandlingOpprettetDto(
    override val kildeReferanse: String,
    val detaljer: DetaljerWrapper,
) : KlageinstanshendelseDto {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun toDomain(
        id: UUID,
        opprettet: Tidspunkt,
    ): Either<KunneIkkeTolkeKlageinstanshendelse.UgyldigeVerdier, TolketKlageinstanshendelse.AnkebehandlingOpprettet> {
        return Either.catch {
            TolketKlageinstanshendelse.AnkebehandlingOpprettet(
                id = id,
                opprettet = opprettet,
                klageId = KlageId(UUID.fromString(kildeReferanse)),
                mottattKlageinstans = parseKabalDatetime(detaljer.ankebehandlingOpprettet.mottattKlageinstans),
            )
        }.mapLeft {
            log.error("Kunne ikke tolke klageinstanshendelse.", it)
            KunneIkkeTolkeKlageinstanshendelse.UgyldigeVerdier
        }
    }

    data class DetaljerWrapper(
        val ankebehandlingOpprettet: Detaljer,
    )

    data class Detaljer(
        // Eksempel: 2024-08-19T14:28:00.28460924
        val mottattKlageinstans: String,
    )
}
