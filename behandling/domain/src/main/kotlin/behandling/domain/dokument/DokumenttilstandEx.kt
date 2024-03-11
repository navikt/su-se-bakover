package behandling.domain.dokument

import behandling.domain.Stønadsbehandling
import dokument.domain.Dokumenttilstand

fun Dokumenttilstand?.setDokumentTilstandBasertPåBehandlingHvisNull(b: Stønadsbehandling): Dokumenttilstand {
    return when (this) {
        null -> when (b.skalSendeVedtaksbrev()) {
            true -> Dokumenttilstand.IKKE_GENERERT_ENDA
            false -> Dokumenttilstand.SKAL_IKKE_GENERERE
        }

        else -> this
    }
}

fun Stønadsbehandling.dokumenttilstandForBrevvalg(): Dokumenttilstand = when (this.skalSendeVedtaksbrev()) {
    false -> Dokumenttilstand.SKAL_IKKE_GENERERE
    true -> Dokumenttilstand.IKKE_GENERERT_ENDA
}
