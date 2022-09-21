package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtale
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtalestatus
import java.time.LocalDate
import java.util.UUID

fun kontrollsamtale(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = UUID.randomUUID(),
    innkallingsdato: LocalDate = fixedLocalDate.plusMonths(4),
    status: Kontrollsamtalestatus = Kontrollsamtalestatus.PLANLAGT_INNKALLING,
    frist: LocalDate = fixedLocalDate.plusMonths(5),
    dokumentId: UUID? = null,
): Kontrollsamtale = Kontrollsamtale(
    id = id,
    opprettet = opprettet,
    sakId = sakId,
    innkallingsdato = innkallingsdato,
    status = status,
    frist = frist,
    dokumentId = dokumentId,
)
