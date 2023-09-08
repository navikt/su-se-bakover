package no.nav.su.se.bakover.service.skatt

import no.nav.su.se.bakover.domain.brev.KunneIkkeJournalføreDokument
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeLageJournalpostUtenforSak
import no.nav.su.se.bakover.domain.skatt.KunneIkkeHenteSkattemelding

sealed interface KunneIkkeGenerereSkattePdfOgJournalføre {
    data class FeilVedGenereringAvPdf(val originalFeil: KunneIkkeHenteOgLagePdfAvSkattegrunnlag) :
        KunneIkkeGenerereSkattePdfOgJournalføre

    data class FeilVedJournalføring(val originalFeil: KunneIkkeJournalføreDokument) :
        KunneIkkeGenerereSkattePdfOgJournalføre

    data class FeilVedHentingAvSkattemelding(val originalFeil: KunneIkkeHenteSkattemelding) :
        KunneIkkeGenerereSkattePdfOgJournalføre

    data class FeilVedJournalpostUtenforSak(val originalFeil: KunneIkkeLageJournalpostUtenforSak) :
        KunneIkkeGenerereSkattePdfOgJournalføre
}
