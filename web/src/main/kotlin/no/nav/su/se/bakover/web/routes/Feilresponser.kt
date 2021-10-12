package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import kotlin.reflect.KClass

internal object Feilresponser {
    val fantIkkeBehandling = NotFound.errorJson(
        "Fant ikke behandling",
        "fant_ikke_behandling",
    )

    val fantIkkePerson = NotFound.errorJson(
        "Fant ikke person",
        "fant_ikke_person",
    )

    val fantIkkeAktørId = NotFound.errorJson(
        "Fant ikke aktør id",
        "fant_ikke_aktør_id",
    )

    val kunneIkkeOppretteOppgave = InternalServerError.errorJson(
        "Kunne ikke opprette oppgave",
        "kunne_ikke_opprette_oppgave",
    )

    val overlappendeVurderingsperioder = BadRequest.errorJson(
        "Vurderingperioder kan ikke overlappe",
        "overlappende_vurderingsperioder",
    )

    val utenforBehandlingsperioden = BadRequest.errorJson(
        "Vurderingsperioden(e) kan ikke være utenfor behandlingsperioden",
        "vurderingsperiode_utenfor_behandlingsperiode",
    )

    val ugyldigBody = BadRequest.errorJson(
        "Ugyldig body",
        "ugyldig_body",
    )

    val ugyldigInput = BadRequest.errorJson(
        "Ugyldig input",
        "ugyldig_input",
    )

    val feilVedGenereringAvDokument = InternalServerError.errorJson(
        "Feil ved generering av dokument",
        "feil_ved_generering_av_dokument",
    )

    fun ugyldigTilstand(fra: KClass<*>, til: KClass<*>): Resultat {
        return BadRequest.errorJson(
            "Kan ikke gå fra tilstanden ${fra.simpleName} til tilstanden ${til.simpleName}",
            "ugyldig_tilstand",
        )
    }

    object Uføre {
        val uføregradMåVæreMellomEnOgHundre = BadRequest.errorJson(
            message = "Uføregrad må være mellom en og hundre",
            code = "uføregrad_må_være_mellom_en_og_hundre",
        )
        val uføregradOgForventetInntektMangler = BadRequest.errorJson(
            "Hvis man innvilger uførevilkåret må man sende med uføregrad og forventet inntekt",
            "uføregrad_og_forventet_inntekt_mangler",
        )
        val periodeForGrunnlagOgVurderingErForskjellig = BadRequest.errorJson(
            "Det er ikke samsvar mellom perioden for vurdering og perioden for grunnlaget",
            "periode_for_grunnlag_og_vurdering_er_forskjellig",
        )
    }

    val kunneIkkeLeggeTilBosituasjonsgrunnlag = BadRequest.errorJson(
        "Kunne ikke legge til bosituasjonsgrunnlag",
        "kunne_ikke_legge_til_bosituasjonsgrunnlag",
    )

    val kunneIkkeLeggeTilFradragsgrunnlag = BadRequest.errorJson(
        "Kunne ikke legge til fradragsgrunnlag",
        "kunne_ikke_legge_til_fradragsgrunnlag",
    )

    val kunneIkkeAvgjøreOmFørstegangEllerNyPeriode = InternalServerError.errorJson(
        "Kunne ikke opprette oppgave siden vi ikke kan avgjøre om det er en FØRSTEGANGSSØKNAD eller NY_PERIODE",
        "kunne_ikke_avgjøre_om_førstegang_eller_ny_periode",
    )

    object Brev {
        val kunneIkkeGenerereBrev = InternalServerError.errorJson(
            "Kunne ikke generere brev",
            "kunne_ikke_generere_brev",
        )

        val kunneIkkeLageBrevutkast = InternalServerError.errorJson(
            "Kunne ikke lage brevutkast",
            "kunne_ikke_lage_brevutkast",
        )
    }

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
