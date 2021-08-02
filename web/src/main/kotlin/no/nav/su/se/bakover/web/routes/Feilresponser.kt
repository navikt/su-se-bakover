package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.web.errorJson

internal object Feilresponser {

    val fantIkkeBehandling = HttpStatusCode.NotFound.errorJson(
        "Fant ikke behandling",
        "fant_ikke_behandling",
    )

    val klarteIkkeHentePerson = HttpStatusCode.NotFound.errorJson(
        "Fant ikke person",
        "fant_ikke_person",
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
    val alleVurderingeneMåHaSammeResultat = HttpStatusCode.BadRequest.errorJson(
        "Alle vurderingsperiodene må ha samme vurdering (ja/nei)",
        "vurderingene_må_ha_samme_resultat",
    )

    val kunneIkkeLageBosituasjon = HttpStatusCode.NotFound.errorJson(
        "Klarte ikke lagre bosituasjon",
        "klarte_ikke_lagre_bosituasjon",
    )

    val ikkeGyldigFødselsnummer = HttpStatusCode.BadRequest.errorJson(
        "Inneholder ikke et gyldig fødselsnummer",
        "ikke_gyldig_fødselsnummer",
    )
    val kanIkkeHaEpsFradragUtenEps = HttpStatusCode.BadRequest.errorJson(
        "Kan ikke ha fradrag knyttet til EPS når bruker ikke har EPS.",
        "kan_ikke_ha_eps_fradrag_uten_eps",
    )
}
