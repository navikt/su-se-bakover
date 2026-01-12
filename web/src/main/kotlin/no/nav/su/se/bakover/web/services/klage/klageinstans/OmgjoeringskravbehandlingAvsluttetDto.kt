package no.nav.su.se.bakover.web.services.klage.klageinstans

import arrow.core.Either
import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.klage.KunneIkkeTolkeKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import java.util.UUID

data class OmgjoeringskravbehandlingAvsluttetDto(
    override val kildeReferanse: String,
    val detaljer: DetaljerWrapper,
) : KlageinstanshendelseDto {

    override fun toDomain(
        id: UUID,
        opprettet: Tidspunkt,
    ): Either<KunneIkkeTolkeKlageinstanshendelse, TolketKlageinstanshendelse> =
        Either.catch {
            TolketKlageinstanshendelse.OmgjoeringskravbehandlingAvsluttet(
                id = id,
                opprettet = opprettet,
                avsluttetTidspunkt = parseKabalDatetime(detaljer.omgjoeringskravbehandlingAvsluttet.avsluttet),
                klageId = KlageId(UUID.fromString(kildeReferanse)),
                utfall = detaljer.omgjoeringskravbehandlingAvsluttet.utfall.toDomain(),
                journalpostIDer = detaljer.omgjoeringskravbehandlingAvsluttet.journalpostReferanser
                    .map { JournalpostId(it) },
            )
        }.mapLeft {
            KunneIkkeTolkeKlageinstanshendelse.UgyldigeVerdier
        }

    data class DetaljerWrapper(
        val omgjoeringskravbehandlingAvsluttet: Detaljer,
    )

    data class Detaljer(
        val avsluttet: String,
        val utfall: KlageinstansUtfallDto,
        val journalpostReferanser: List<String>,
    )
}
