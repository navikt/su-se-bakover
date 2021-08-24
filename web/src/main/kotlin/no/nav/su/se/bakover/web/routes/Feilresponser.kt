package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson

internal object Feilresponser {

    val fantIkkeBehandling = HttpStatusCode.NotFound.errorJson(
        "Fant ikke behandling",
        "fant_ikke_behandling",
    )

    val fantIkkePerson = HttpStatusCode.NotFound.errorJson(
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

    val ugyldigBody = HttpStatusCode.BadRequest.errorJson(
        "Ugyldig body",
        "ugyldig_body"
    )

    val ugyldigInput = HttpStatusCode.BadRequest.errorJson(
        "Ugyldig input",
        "ugyldig_input"
    )

    internal fun SimuleringFeilet.tilResultat(): Resultat {
        return when (this) {
            SimuleringFeilet.OPPDRAG_UR_ER_STENGT -> HttpStatusCode.InternalServerError.errorJson(
                "Simuleringsfeil: Oppdrag/UR er stengt eller nede", "simulering_feilet_oppdrag_stengt_eller_nede",
            )
            SimuleringFeilet.PERSONEN_FINNES_IKKE_I_TPS -> HttpStatusCode.InternalServerError.errorJson(
                "Simuleringsfeil: Finner ikke person i TPS", "simulering_feilet_finner_ikke_person_i_tps",
            )
            SimuleringFeilet.FINNER_IKKE_KJØREPLANSPERIODE_FOR_FOM -> HttpStatusCode.InternalServerError.errorJson(
                "Simuleringsfeil: Finner ikke kjøreplansperiode for fom-dato",
                "simulering_feilet_finner_ikke_kjøreplansperiode_for_fom",
            )
            SimuleringFeilet.OPPDRAGET_FINNES_IKKE -> HttpStatusCode.InternalServerError.errorJson(
                "Simuleringsfeil: Oppdraget finnes ikke fra før", "simulering_feilet_oppdraget_finnes_ikke",
            )
            SimuleringFeilet.FUNKSJONELL_FEIL, SimuleringFeilet.TEKNISK_FEIL -> HttpStatusCode.InternalServerError.errorJson(
                "Simulering feilet",
                "simulering_feilet",
            )
        }
    }
}
