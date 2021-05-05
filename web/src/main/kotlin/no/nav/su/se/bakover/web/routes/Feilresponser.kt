package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.web.errorJson

internal object Feilresponser {

    val fantIkkeBehandling = HttpStatusCode.NotFound.errorJson(
        "fant ikke behandling",
        "fant_ikke_behandling",
    )

    val overlappendeVurderingsperioder = HttpStatusCode.BadRequest.errorJson(
        "Vurderingperioder kan ikke overlappe",
        "overlappende_vurderingsperioder",
    )

    val periodeForGrunnlagOgVurderingErForskjellig = HttpStatusCode.BadRequest.errorJson(
        "Det er ikke samsvar mellom perioden for vurdering og perioden for grunnlaget",
        "periode_for_grunnlag_og_vurdering_er_forskjellig",
    )

    val uføregradOgForventetInntektMangler = HttpStatusCode.BadRequest.errorJson(
        "Hvis man innvilger uførevilkåret må man sende med uføregrad og forventet inntekt",
        "uføregrad_og_forventet_inntekt_mangler",
    )
    val utenforBehandlingsperioden = HttpStatusCode.BadRequest.errorJson(
        "Vurderingsperioden(e) kan ikke være utenfor behandlingsperioden",
        "vurderingsperiode_utenfor_behandlingsperiode",
    )
}
