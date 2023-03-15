package no.nav.su.se.bakover.domain.dokument

import no.nav.su.se.bakover.domain.behandling.Behandling

/**
 * Vurder om denne typen kun gjelder for [Vedtak] eller om den kan gjelde for behandlingene, saken også.
 *
 * Et dokument har et livsvløp:
 * 1) Saksbehandler og/eller systemet bestemmer om vi skal sende et vedtaksdokument (brev)
 * 2) Innsamling av informasjon. F.eks: saksbehandlers navn, attestants navn og tilbakekrevingsinformasjon (skatt).
 * 3) Når vi har all informasjonen genererer en jobb dokumentet. Og vi prøver snarest og:
 * 4) Journalfører brevet.
 * 5) Sender brevet.
 */
enum class Dokumenttilstand {
    SKAL_IKKE_GENERERE,
    IKKE_GENERERT_ENDA,
    GENERERT,
    JOURNALFØRT,
    SENDT,
}

fun Dokumenttilstand?.setDokumentTilstandBasertPåBehandlingHvisNull(b: Behandling): Dokumenttilstand {
    return when (this) {
        null -> when (b.skalSendeVedtaksbrev()) {
            true -> Dokumenttilstand.IKKE_GENERERT_ENDA
            false -> Dokumenttilstand.SKAL_IKKE_GENERERE
        }

        else -> this
    }
}

fun Behandling.dokumenttilstandForBrevvalg(): Dokumenttilstand = when (this.skalSendeVedtaksbrev()) {
    false -> Dokumenttilstand.SKAL_IKKE_GENERERE
    true -> Dokumenttilstand.IKKE_GENERERT_ENDA
}
