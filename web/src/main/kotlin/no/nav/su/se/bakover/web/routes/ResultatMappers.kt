package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkAvTidslinjeOgSimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkFeil
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.StøtterIkkeOverlappendeStønadsperioder
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService

internal fun UtbetalingFeilet.tilResultat(): Resultat {
    return when (this) {
        is UtbetalingFeilet.KunneIkkeSimulere -> this.simuleringFeilet.tilResultat()
        UtbetalingFeilet.Protokollfeil -> HttpStatusCode.InternalServerError.errorJson(
            "Kunne ikke utføre utbetaling",
            "kunne_ikke_utbetale",
        )
        is UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> this.feil.tilResultat()
        UtbetalingFeilet.FantIkkeSak -> HttpStatusCode.InternalServerError.errorJson("Fant ikke sak", "kunne_ikke_finne_sak")
    }
}

internal fun SimulerUtbetalingFeilet.tilResultat(): Resultat {
    return when (this) {
        is SimulerUtbetalingFeilet.FeilVedKryssjekkAvSaksbehandlerOgAttestantsSimulering -> tilResultat()
        is SimulerUtbetalingFeilet.FeilVedKryssjekkAvTidslinjeOgSimulering -> tilResultat()
        is SimulerUtbetalingFeilet.FeilVedSimulering -> tilResultat()
        is SimulerUtbetalingFeilet.Avkorting -> HttpStatusCode.BadRequest.errorJson(
            "Opphør med avkorting: Siste måned i revurderingsperioden kan ikke være utbetalt.",
            "siste_måned_i_revurderingsperiode_kan_ikke_være_utbetalt_ved_opphør_avkorting",
        )
    }
}

internal fun SimulerUtbetalingFeilet.FeilVedKryssjekkAvSaksbehandlerOgAttestantsSimulering.tilResultat(): Resultat {
    return this.feil.tilResultat()
}

internal fun SimulerUtbetalingFeilet.FeilVedKryssjekkAvTidslinjeOgSimulering.tilResultat(): Resultat {
    return this.feil.tilResultat()
}

internal fun SimulerUtbetalingFeilet.FeilVedSimulering.tilResultat(): Resultat {
    return this.feil.tilResultat()
}

internal fun KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.tilResultat(): Resultat {
    return when (this) {
        KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikFeilutbetaling -> {
            HttpStatusCode.InternalServerError.errorJson(
                "Kryssjekk av saksbehandlers og attestants simulering feilet - ulik verdi for feilutbetaling",
                "kontrollsimulering_ulik_saksbehandlers_simulering",
            )
        }
        KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikGjelderId -> {
            HttpStatusCode.InternalServerError.errorJson(
                "Kryssjekk av saksbehandlers og attestants simulering feilet - ulik verdi for gjelder id",
                "kontrollsimulering_ulik_saksbehandlers_simulering",
            )
        }
        KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikPeriode -> {
            HttpStatusCode.InternalServerError.errorJson(
                "Kryssjekk av saksbehandlers og attestants simulering feilet - ulik verdi for periode",
                "kontrollsimulering_ulik_saksbehandlers_simulering",
            )
        }
        KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UliktBeløp -> {
            HttpStatusCode.InternalServerError.errorJson(
                "Kryssjekk av saksbehandlers og attestants simulering feilet - ulik verdi for beløp",
                "kontrollsimulering_ulik_saksbehandlers_simulering",
            )
        }

        KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.FantIngenGjeldendeUtbetalingerForDato -> {
            HttpStatusCode.InternalServerError.errorJson(
                "Kryssjekk av saksbehandlers og attestants simulering feilet - fant ikke gjeldende utbetaling for dato",
                "kontrollsimulering_ulik_saksbehandlers_simulering",
            )
        }
    }
}

internal fun SøknadsbehandlingService.KunneIkkeSimulereBehandling.tilResultat(): Resultat {
    return when (this) {
        SøknadsbehandlingService.KunneIkkeSimulereBehandling.FantIkkeBehandling -> {
            Feilresponser.fantIkkeBehandling
        }
        is SøknadsbehandlingService.KunneIkkeSimulereBehandling.KunneIkkeSimulere -> {
            when (val nested = this.feil) {
                is KunneIkkeSimulereBehandling.KunneIkkeSimulere -> {
                    nested.feil.tilResultat()
                }
                is KunneIkkeSimulereBehandling.UgyldigTilstand -> {
                    Feilresponser.ugyldigTilstand(nested.fra, nested.til)
                }
            }
        }
    }
}

internal fun SimuleringFeilet.tilResultat(): Resultat {
    return when (this) {
        SimuleringFeilet.UtenforÅpningstid -> HttpStatusCode.InternalServerError.errorJson(
            "Simuleringsfeil: Oppdrag/UR er stengt eller nede",
            "simulering_feilet_oppdrag_stengt_eller_nede",
        )
        SimuleringFeilet.PersonFinnesIkkeITPS -> HttpStatusCode.InternalServerError.errorJson(
            "Simuleringsfeil: Finner ikke person i TPS",
            "simulering_feilet_finner_ikke_person_i_tps",
        )
        SimuleringFeilet.FinnerIkkeKjøreplanForFraOgMed -> HttpStatusCode.InternalServerError.errorJson(
            "Simuleringsfeil: Finner ikke kjøreplansperiode for fom-dato",
            "simulering_feilet_finner_ikke_kjøreplansperiode_for_fom",
        )
        SimuleringFeilet.OppdragEksistererIkke -> HttpStatusCode.InternalServerError.errorJson(
            "Simuleringsfeil: Oppdraget finnes ikke fra før",
            "simulering_feilet_oppdraget_finnes_ikke",
        )
        SimuleringFeilet.FunksjonellFeil, SimuleringFeilet.TekniskFeil -> HttpStatusCode.InternalServerError.errorJson(
            "Simulering feilet",
            "simulering_feilet",
        )
    }
}

internal fun KryssjekkAvTidslinjeOgSimuleringFeilet.tilResultat(): Resultat {
    return when (this) {
        is KryssjekkAvTidslinjeOgSimuleringFeilet.KryssjekkFeilet -> {
            when (this.feil) {
                is KryssjekkFeil.KombinasjonAvSimulertTypeOgTidslinjeTypeErUgyldig -> {
                    Feilresponser.kryssjekkTidslinjeSimuleringFeilet
                }
                is KryssjekkFeil.SimulertBeløpOgTidslinjeBeløpErForskjellig -> {
                    Feilresponser.kryssjekkTidslinjeSimuleringFeilet
                }
                is KryssjekkFeil.StansMedFeilutbetaling -> {
                    HttpStatusCode.BadRequest.errorJson(
                        "Stans vil føre til feilutbetalinger",
                        "stans_fører_til_feilutbetaling",
                    )
                }

                is KryssjekkFeil.GjenopptakMedFeilutbetaling -> {
                    HttpStatusCode.InternalServerError.errorJson(
                        "Gjenopptat fører til feilutbetaling",
                        "gjenopptak_fører_til_feilutbetaling",
                    )
                }
            }
        }
        KryssjekkAvTidslinjeOgSimuleringFeilet.RekonstruertUtbetalingsperiodeErUlikOpprinnelig -> {
            HttpStatusCode.InternalServerError.errorJson(
                "Rekonstruert utbetalingshistorikk er ikke lik opprinnelig.",
                "rekonstruert_utbetalingshistorikk_ulik_original",
            )
        }
        KryssjekkAvTidslinjeOgSimuleringFeilet.KunneIkkeGenerereTidslinje -> {
            HttpStatusCode.InternalServerError.errorJson(
                "Kunne ikke generere tidslinje",
                "kunne_ikke_generere_tidslinje",
            )
        }
        is KryssjekkAvTidslinjeOgSimuleringFeilet.KunneIkkeSimulere -> {
            this.feil.tilResultat()
        }
    }
}

internal fun StøtterIkkeOverlappendeStønadsperioder.tilResultat() = when (this) {
    StøtterIkkeOverlappendeStønadsperioder.StønadsperiodeForSenerePeriodeEksisterer -> HttpStatusCode.BadRequest.errorJson(
        message = "Kan ikke opprette stønadsperiode som er før en tidligere stønadsperiode.",
        code = "senere_stønadsperiode",
    )
    StøtterIkkeOverlappendeStønadsperioder.StønadsperiodeOverlapperMedIkkeOpphørtStønadsperiode -> HttpStatusCode.BadRequest.errorJson(
        message = "Kan ikke overlappe med tidligere utbetalte stønadsperioder eller ikke-opphørte stønadsperioder.",
        code = "overlappende_stønadsperiode",
    )
    StøtterIkkeOverlappendeStønadsperioder.StønadsperiodeInneholderAvkortingPgaUtenlandsopphold -> HttpStatusCode.BadRequest.errorJson(
        message = "Kan ikke overlappe med stønadsmåned som har blitt opphørt og ført til avkortingsvarsel.",
        code = "overlappende_stønadsperiode",
    )
    StøtterIkkeOverlappendeStønadsperioder.StønadsperiodeInneholderFeilutbetaling -> HttpStatusCode.BadRequest.errorJson(
        message = "Kan ikke overlappe med stønadsmåned som har blitt opphørt og ført til feilutbetaling.",
        code = "overlappende_stønadsperiode",
    )
}
