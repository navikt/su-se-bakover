package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson

internal object Feilresponser {

    val fantIkkeBehandling = NotFound.errorJson(
        "Fant ikke behandling",
        "fant_ikke_behandling",
    )

    val fantIkkePerson = NotFound.errorJson(
        "Fant ikke person",
        "fant_ikke_person",
    )

    val overlappendeVurderingsperioder = BadRequest.errorJson(
        "Vurderingperioder kan ikke overlappe",
        "overlappende_vurderingsperioder",
    )

    val periodeForGrunnlagOgVurderingErForskjellig = BadRequest.errorJson(
        "Det er ikke samsvar mellom perioden for vurdering og perioden for grunnlaget",
        "periode_for_grunnlag_og_vurdering_er_forskjellig",
    )

    val uføregradOgForventetInntektMangler = BadRequest.errorJson(
        "Hvis man innvilger uførevilkåret må man sende med uføregrad og forventet inntekt",
        "uføregrad_og_forventet_inntekt_mangler",
    )
    val utenforBehandlingsperioden = BadRequest.errorJson(
        "Vurderingsperioden(e) kan ikke være utenfor behandlingsperioden",
        "vurderingsperiode_utenfor_behandlingsperiode",
    )
    val alleVurderingeneMåHaSammeResultat = BadRequest.errorJson(
        "Alle vurderingsperiodene må ha samme vurdering (ja/nei)",
        "vurderingene_må_ha_samme_resultat",
    )

    val kunneIkkeLeggeTilBosituasjonsgrunnlag = BadRequest.errorJson(
        "Kunne ikke legge til bosituasjonsgrunnlag",
        "kunne_ikke_legge_til_bosituasjonsgrunnlag",
    )

    val kunneIkkeLeggeTilFradragsgrunnlag = BadRequest.errorJson(
        "Kunne ikke legge til fradragsgrunnlag",
        "kunne_ikke_legge_til_fradragsgrunnlag",
    )

    val ikkeGyldigFødselsnummer = BadRequest.errorJson(
        "Inneholder ikke et gyldig fødselsnummer",
        "ikke_gyldig_fødselsnummer",
    )
    val kanIkkeHaEpsFradragUtenEps = BadRequest.errorJson(
        "Kan ikke ha fradrag knyttet til EPS når bruker ikke har EPS.",
        "kan_ikke_ha_eps_fradrag_uten_eps",
    )

    val ugyldigBody = BadRequest.errorJson(
        "Ugyldig body",
        "ugyldig_body",
    )

    val ugyldigInput = BadRequest.errorJson(
        "Ugyldig input",
        "ugyldig_input",
    )

    internal fun UtbetalingFeilet.tilResultat(): Resultat {
        return when (this) {
            is UtbetalingFeilet.KunneIkkeSimulere -> this.simuleringFeilet.tilResultat()
            UtbetalingFeilet.Protokollfeil -> InternalServerError.errorJson(
                "Kunne ikke utføre utbetaling",
                "kunne_ikke_utbetale",
            )
            UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> InternalServerError.errorJson(
                "Oppdaget inkonsistens mellom tidligere utført simulering og kontrollsimulering. Ny simulering må utføres og kontrolleres før iverksetting kan gjennomføres",
                "kontrollsimulering_ulik_saksbehandlers_simulering",
            )
            UtbetalingFeilet.FantIkkeSak -> InternalServerError.errorJson("Fant ikke sak", "kunne_ikke_finne_sak")
            UtbetalingFeilet.KontrollAvSimuleringFeilet -> InternalServerError.errorJson(
                "Kontroll av simulering feilet. Inkonsistens må undersøkes",
                "kontroll_av_simulering_feilet",
            )
        }
    }

    internal fun SimuleringFeilet.tilResultat(): Resultat {
        return when (this) {
            SimuleringFeilet.OPPDRAG_UR_ER_STENGT -> InternalServerError.errorJson(
                "Simuleringsfeil: Oppdrag/UR er stengt eller nede", "simulering_feilet_oppdrag_stengt_eller_nede",
            )
            SimuleringFeilet.PERSONEN_FINNES_IKKE_I_TPS -> InternalServerError.errorJson(
                "Simuleringsfeil: Finner ikke person i TPS", "simulering_feilet_finner_ikke_person_i_tps",
            )
            SimuleringFeilet.FINNER_IKKE_KJØREPLANSPERIODE_FOR_FOM -> InternalServerError.errorJson(
                "Simuleringsfeil: Finner ikke kjøreplansperiode for fom-dato",
                "simulering_feilet_finner_ikke_kjøreplansperiode_for_fom",
            )
            SimuleringFeilet.OPPDRAGET_FINNES_IKKE -> InternalServerError.errorJson(
                "Simuleringsfeil: Oppdraget finnes ikke fra før", "simulering_feilet_oppdraget_finnes_ikke",
            )
            SimuleringFeilet.FUNKSJONELL_FEIL, SimuleringFeilet.TEKNISK_FEIL -> InternalServerError.errorJson(
                "Simulering feilet",
                "simulering_feilet",
            )
        }
    }
}
