package no.nav.su.se.bakover.web.routes.grunnlag

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.service.vilkår.LeggTilFormuevilkårRequest

internal fun LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet.tilResultat() = when (val f = this) {
    LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet.FormuePeriodeErUtenforBehandlingsperioden -> HttpStatusCode.BadRequest.errorJson(
        "Ikke lov med formueperiode utenfor behandlingsperioden",
        "ikke_lov_med_formueperiode_utenfor_behandlingsperioden",
    )
    LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet.IkkeLovMedOverlappendePerioder -> Feilresponser.overlappendeVurderingsperioder
    is LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet.Konsistenssjekk -> f.feil.tilResultat()
}
