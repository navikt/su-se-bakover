package no.nav.su.se.bakover.domain.dokument

sealed interface KunneIkkeLageDokument {

    /**
     * Dette er steget før vi genererer PDFen / dokumentet.
     */
    data class FeilVedHentingAvInformasjon(
        val underliggende: no.nav.su.se.bakover.domain.brev.jsonRequest.FeilVedHentingAvInformasjon,
    ) : KunneIkkeLageDokument

    /**
     * Vi klarte innhente nødvendig informasjon, men noe feil skjedde under genereringa av PDFen.
     */
    data object FeilVedGenereringAvPdf : KunneIkkeLageDokument
}
