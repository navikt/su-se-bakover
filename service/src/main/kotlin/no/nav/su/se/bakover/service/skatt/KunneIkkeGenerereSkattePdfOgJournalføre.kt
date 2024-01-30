package no.nav.su.se.bakover.service.skatt

import dokument.domain.brev.KunneIkkeJournalføreDokument
import vilkår.skatt.domain.KunneIkkeHenteSkattemelding
import vilkår.skatt.domain.journalpost.KunneIkkeLageJournalpostUtenforSak

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
