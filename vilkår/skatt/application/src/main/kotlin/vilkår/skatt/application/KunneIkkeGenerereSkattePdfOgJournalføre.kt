package vilkår.skatt.application

import dokument.domain.brev.KunneIkkeJournalføreDokument
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import person.domain.KunneIkkeHentePerson
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

    data class SakstypeErIkkeDenSammeSomForespurt(val faktiskSakstype: Sakstype, val forespurtSakstype: Sakstype) :
        KunneIkkeGenerereSkattePdfOgJournalføre

    data class FeilVedHentingAvPerson(val it: KunneIkkeHentePerson) : KunneIkkeGenerereSkattePdfOgJournalføre
    data object FeilVedKonverteringAvFagsystemIdTilSaksnummer : KunneIkkeGenerereSkattePdfOgJournalføre
    data object FikkTilbakeEtAnnetFnrFraPdlEnnDetSomBleSendtInn : KunneIkkeGenerereSkattePdfOgJournalføre
    data object UføresaksnummerKanIkkeBrukesForAlder : KunneIkkeGenerereSkattePdfOgJournalføre
    data object FnrPåSakErIkkeLikFnrViFikkFraPDL : KunneIkkeGenerereSkattePdfOgJournalføre
    data object FantIkkeSak : KunneIkkeGenerereSkattePdfOgJournalføre
}
