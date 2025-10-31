package no.nav.su.se.bakover.web.services.klage.klageinstans

import arrow.core.Either
import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.klage.KunneIkkeTolkeKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import org.slf4j.LoggerFactory
import java.util.UUID

data class AnkebehandlingAvsluttetDto(
    override val kildeReferanse: String,
    val detaljer: DetaljerWrapper,
) : KlageinstanshendelseDto {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun toDomain(
        id: UUID,
        opprettet: Tidspunkt,
    ): Either<KunneIkkeTolkeKlageinstanshendelse, TolketKlageinstanshendelse.AnkebehandlingAvsluttet> {
        return Either.catch {
            TolketKlageinstanshendelse.AnkebehandlingAvsluttet(
                id = id,
                opprettet = opprettet,
                klageId = KlageId(UUID.fromString(kildeReferanse)),
                utfall = detaljer.ankebehandlingAvsluttet.utfall.toDomain(),
                journalpostIDer = detaljer.ankebehandlingAvsluttet.journalpostReferanser.map { JournalpostId(it) },
                avsluttetTidspunkt = parseKabalDatetime(detaljer.ankebehandlingAvsluttet.avsluttet),
            )
        }.mapLeft {
            log.error("Kunne ikke tolke klageinstanshendelse.", it)
            KunneIkkeTolkeKlageinstanshendelse.UgyldigeVerdier
        }
    }

    data class DetaljerWrapper(
        val ankebehandlingAvsluttet: Detaljer,
    )

    data class Detaljer(
        val avsluttet: String,
        val utfall: KlageinstansUtfallDto,
        val journalpostReferanser: List<String>,
    )
}
