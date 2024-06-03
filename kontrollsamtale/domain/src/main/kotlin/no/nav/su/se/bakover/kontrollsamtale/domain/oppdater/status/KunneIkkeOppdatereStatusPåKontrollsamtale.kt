package no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.status

import dokument.domain.journalføring.KunneIkkeSjekkeTilknytningTilSak
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtalestatus
import java.util.UUID

sealed interface KunneIkkeOppdatereStatusPåKontrollsamtale {
    data class UgyldigStatusovergang(
        val kontrollsamtaleId: UUID,
        val gyldigeOverganger: Set<Kontrollsamtalestatus>,
    ) : KunneIkkeOppdatereStatusPåKontrollsamtale

    data class FeilVedHentingAvJournalpost(
        val underliggendeFeil: KunneIkkeSjekkeTilknytningTilSak,
        val journalpostId: JournalpostId,
        val saksnummer: Saksnummer,
    ) : KunneIkkeOppdatereStatusPåKontrollsamtale

    data class JournalpostIkkeTilknyttetSak(
        val journalpostId: JournalpostId,
        val saksnummer: Saksnummer,
    ) : KunneIkkeOppdatereStatusPåKontrollsamtale
}
