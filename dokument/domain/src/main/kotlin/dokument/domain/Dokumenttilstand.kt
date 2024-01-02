package dokument.domain

/**
 * Vurder om denne typen kun gjelder for [no.nav.su.se.bakover.vedtak.domain.Vedtak] eller om den kan gjelde for behandlingene, saken også (e.g. forhåndsvarsel).
 *
 * Et dokument har et livsvløp:
 * 1) Saksbehandler og/eller systemet bestemmer om vi skal sende et vedtaksdokument (brev)
 * 2) Innsamling av informasjon. F.eks: saksbehandlers navn, attestants navn og tilbakekrevingsinformasjon (skatt).
 * 3) Når vi har all informasjonen genererer en jobb dokumentet. Og vi prøver snarest å:
 * 4) Journalføre brevet.
 * 5) Sende brevet.
 */
enum class Dokumenttilstand {
    SKAL_IKKE_GENERERE,
    IKKE_GENERERT_ENDA,
    GENERERT,
    JOURNALFØRT,
    SENDT,
}
