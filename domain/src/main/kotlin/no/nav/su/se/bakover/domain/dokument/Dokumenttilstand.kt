package no.nav.su.se.bakover.domain.dokument

import dokument.domain.Dokumenttilstand
import no.nav.su.se.bakover.domain.behandling.Behandling

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
