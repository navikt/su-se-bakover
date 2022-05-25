package no.nav.su.se.bakover.web.routes.grunnlag

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.service.vilkår.LeggTilFormuegrunnlagRequest
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.routes.Feilresponser

internal fun LeggTilFormuegrunnlagRequest.KunneIkkeMappeTilDomenet.tilResultat() = when (val f = this) {
    LeggTilFormuegrunnlagRequest.KunneIkkeMappeTilDomenet.FormuePeriodeErUtenforBehandlingsperioden -> HttpStatusCode.BadRequest.errorJson(
        "Ikke lov med formueperiode utenfor behandlingsperioden",
        "ikke_lov_med_formueperiode_utenfor_behandlingsperioden",
    )
    LeggTilFormuegrunnlagRequest.KunneIkkeMappeTilDomenet.IkkeLovMedOverlappendePerioder -> Feilresponser.overlappendeVurderingsperioder
    is LeggTilFormuegrunnlagRequest.KunneIkkeMappeTilDomenet.Konsistenssjekk -> f.feil.tilResultat()
}
