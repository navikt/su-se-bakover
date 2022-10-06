package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtale
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtalestatus
import java.time.LocalDate
import java.util.UUID

fun planlagtKontrollsamtale(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = UUID.randomUUID(),
    innkallingsdato: LocalDate = fixedLocalDate.plusMonths(4),
    frist: LocalDate = fixedLocalDate.plusMonths(5),
): Kontrollsamtale {
    return Kontrollsamtale(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        innkallingsdato = innkallingsdato,
        status = Kontrollsamtalestatus.PLANLAGT_INNKALLING,
        frist = frist,
        dokumentId = null,
        journalpostIdKontrollnotat = null,
    )
}

fun innkaltKontrollsamtale(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = UUID.randomUUID(),
    innkallingsdato: LocalDate = fixedLocalDate.plusMonths(4),
    frist: LocalDate = fixedLocalDate.plusMonths(5),
    dokumentId: UUID = UUID.randomUUID(),
): Kontrollsamtale {
    return planlagtKontrollsamtale(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        innkallingsdato = innkallingsdato,
        frist = frist,
    ).settInnkalt(
        dokumentId = dokumentId,
    ).getOrFail()
}

fun gjennomførtKontrollsamtale(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = UUID.randomUUID(),
    innkallingsdato: LocalDate = fixedLocalDate.plusMonths(4),
    frist: LocalDate = fixedLocalDate.plusMonths(5),
    dokumentId: UUID = UUID.randomUUID(),
    journalpostId: JournalpostId = JournalpostId("done and done"),
): Kontrollsamtale {
    return planlagtKontrollsamtale(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        innkallingsdato = innkallingsdato,
        frist = frist,
    ).settInnkalt(
        dokumentId = dokumentId,
    ).getOrFail()
        .settGjennomført(
            journalpostId = journalpostId,
        ).getOrFail()
}
