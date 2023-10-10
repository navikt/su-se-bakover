package dokument.domain

sealed interface KunneIkkeLageDokument {

    /**
     * Dette er steget før vi genererer PDFen / dokumentet.
     * se [no.nav.su.se.bakover.domain.brev.jsonRequest.FeilVedHentingAvInformasjon]
     */
    data object FeilVedHentingAvInformasjon : KunneIkkeLageDokument

    /**
     * Vi klarte innhente nødvendig informasjon, men noe feil skjedde under genereringa av PDFen.
     */
    data object FeilVedGenereringAvPdf : KunneIkkeLageDokument
}
