package no.nav.su.se.bakover.web.routes.dokument

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.domain.brev.jsonRequest.FeilVedHentingAvInformasjon
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument

fun KunneIkkeLageDokument.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeLageDokument.FeilVedHentingAvInformasjon -> this.underliggende.tilResultat()
        is KunneIkkeLageDokument.FeilVedGenereringAvPdf -> Feilresponser.feilVedGenereringAvDokument
    }
}
fun FeilVedHentingAvInformasjon.tilResultat(): Resultat {
    return when (this) {
        is FeilVedHentingAvInformasjon.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> Feilresponser.fantIkkeSaksbehandlerEllerAttestant.copy(
            httpCode = HttpStatusCode.InternalServerError,
        )
        is FeilVedHentingAvInformasjon.KunneIkkeHentePerson -> Feilresponser.fantIkkePerson.copy(
            httpCode = HttpStatusCode.InternalServerError,
        )
    }
}
