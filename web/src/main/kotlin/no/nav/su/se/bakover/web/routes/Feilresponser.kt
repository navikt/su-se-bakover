package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
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

    val fantIkkeSak = NotFound.errorJson(
        "Fant ikke sak",
        "fant_ikke_sak",
    )

    val fantIkkeVedtak = NotFound.errorJson(
        "Fant ikke vedtak",
        "fant_ikke_vedtak",
    )

    val ikkeTilgangTilPerson = Forbidden.errorJson(
        "Ikke tilgang til å se person", "ikke_tilgang_til_person",
    )

    val fantIkkePerson = NotFound.errorJson(
        "Fant ikke person",
        "fant_ikke_person",
    )

    val feilVedOppslagPåPerson = InternalServerError.errorJson(
        "Feil ved oppslag på person", "feil_ved_oppslag_person",
    )

    val fantIkkeSøknad = NotFound.errorJson(
        "Fant ikke søknad",
        "fant_ikke_søknad",
    )

    val fantIkkeKlage = NotFound.errorJson(
        "Fant ikke klage",
        "fant_ikke_klage",
    )

    val harAlleredeÅpenBehandling = BadRequest.errorJson(
        "Har allerede en aktiv behandling",
        "har_allerede_en_aktiv_behandling",
    )

    val søknadHarBehandlingFraFør = BadRequest.errorJson(
        "Søknad har en behandling fra før",
        "søknad_har_behandling_fra_før",
    )

    val fantIkkeAktørId = NotFound.errorJson(
        "Fant ikke aktør id",
        "fant_ikke_aktør_id",
    )

    val kunneIkkeOppretteOppgave = InternalServerError.errorJson(
        "Kunne ikke opprette oppgave",
        "kunne_ikke_opprette_oppgave",
    )

    val søknadManglerOppgave = InternalServerError.errorJson(
        "Søknad mangler oppgave",
        "søknad_mangler_oppgave",
    )

    val overlappendeVurderingsperioder = BadRequest.errorJson(
        "Vurderingperioder kan ikke overlappe",
        "overlappende_vurderingsperioder",
    )

    val søknadErLukket = BadRequest.errorJson(
        "Søknad er allerede lukket",
        "søknad_er_allerede_lukket",
    )

    val utenforBehandlingsperioden = BadRequest.errorJson(
        "Vurderingsperioden(e) kan ikke være utenfor behandlingsperioden",
        "vurderingsperiode_utenfor_behandlingsperiode",
    )

    val alleResultaterMåVæreLike = BadRequest.errorJson(
        "Vurderingsperioden(e) kan ikke inneholde forskjellige resultater",
        "vurderingsperiode_kan_ikke_inneholde_forskjellige_resultater",
    )

    val måVurdereHelePerioden = BadRequest.errorJson(
        "Må vurdere hele perioden",
        "må_vurdere_hele_perioden",
    )

    val måInnheholdeKunEnVurderingsperiode = BadRequest.errorJson(
        "Må innheholde kun en vurderingsperiode",
        "må_inneholde_kun_en_vurderingsperiode",
    )

    val ugyldigBody = BadRequest.errorJson(
        "Ugyldig body",
        "ugyldig_body",
    )

    val ugyldigInput = BadRequest.errorJson(
        "Ugyldig input",
        "ugyldig_input",
    )

    val ugyldigFødselsnummer = BadRequest.errorJson(
        "Ugyldig fødselsnummer",
        "ugyldig_fødselsnummer",
    )

    val feilVedGenereringAvDokument = InternalServerError.errorJson(
        "Feil ved generering av dokument",
        "feil_ved_generering_av_dokument",
    )

    val behandlingErIUgyldigTilstand = InternalServerError.errorJson(
        "Behandlingen er i ugyldig tilstand for avslag",
        "behandling_i_ugyldig_tilstand_for_avslag",
    )

    val fantIkkeSaksbehandlerEllerAttestant = NotFound.errorJson(
        "Fant ikke saksbehandler eller attestant",
        "fant_ikke_saksbehandler_eller_attestant",
    )
    val fantIkkeGjeldendeUtbetaling = NotFound.errorJson(
        "Fant ikke gjeldende utbetaling",
        "fant_ikke_gjeldende_utbetaling",
    )

    val attestantOgSaksbehandlerKanIkkeVæreSammePerson = Forbidden.errorJson(
        "Attestant og saksbehandler kan ikke være samme person",
        "attestant_og_saksbehandler_kan_ikke_være_samme_person",
    )

    val kunneIkkeSimulere = InternalServerError.errorJson(
        "Kunne ikke simulere",
        "kunne_ikke_simulere",
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

    val depositumErHøyereEnnInnskudd = BadRequest.errorJson(
        "Depositum er høyere enn innskudd",
        "depositum_høyere_enn_innskudd",
    )

    val periodeForGrunnlagOgVurderingErForskjellig = BadRequest.errorJson(
        "Det er ikke samsvar mellom perioden for vurdering og perioden for grunnlaget",
        "periode_for_grunnlag_og_vurdering_er_forskjellig",
    )

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

    val harIkkeEktefelle = BadRequest.errorJson(
        "Kan ikke ha formue for eps når søker ikke har eps",
        "har_ikke_ektefelle",
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
