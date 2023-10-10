package no.nav.su.se.bakover.web.routes.dokument

import dokument.domain.KunneIkkeLageDokument
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.domain.brev.jsonRequest.FeilVedHentingAvInformasjon

fun KunneIkkeLageDokument.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeLageDokument.FeilVedHentingAvInformasjon -> HttpStatusCode.InternalServerError.errorJson(
            "Feil ved henting av personinformasjon",
            "feil_ved_henting_av_personInformasjon",
        )

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
