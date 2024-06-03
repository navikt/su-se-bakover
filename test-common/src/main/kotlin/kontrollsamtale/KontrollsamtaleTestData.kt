package no.nav.su.se.bakover.test.kontrollsamtale

import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtalestatus
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import java.time.LocalDate
import java.util.UUID

// TODO jah: Skriv om til å bruk Sak.opprettKontrollsamtale
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

// TODO jah: Skriv om til å bruk Sak.opprettKontrollsamtale
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

// TODO jah: Skriv om til å bruk Sak.opprettKontrollsamtale
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
