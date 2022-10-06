package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.domain.oppdrag.FeilVedKryssjekkAvTidslinjerOgSimulering
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService

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

        is SimuleringFeilet.KontrollAvSimuleringFeilet -> this.feil.tilResultat()
    }
}

internal fun FeilVedKryssjekkAvTidslinjerOgSimulering.tilResultat(): Resultat {
    return when (this) {
        is FeilVedKryssjekkAvTidslinjerOgSimulering.Gjenopptak.FeilVedSjekkAvTidslinjeMotSimulering -> {
            Feilresponser.kryssjekkTidslinjeSimuleringFeilet
        }
        is FeilVedKryssjekkAvTidslinjerOgSimulering.NyEllerOpphør.FeilVedSjekkAvTidslinjeMotSimulering -> {
            Feilresponser.kryssjekkTidslinjeSimuleringFeilet
        }
        FeilVedKryssjekkAvTidslinjerOgSimulering.NyEllerOpphør.RekonstruertUtbetalingsperiodeErUlikOpprinnelig -> {
            HttpStatusCode.InternalServerError.errorJson(
                "Rekonstruert utbetalingshistorikk er ikke lik opprinnelig.",
                "rekonstruert_utbetalingshistorikk_ulik_original",
            )
        }
        FeilVedKryssjekkAvTidslinjerOgSimulering.Stans.SimuleringHarFeilutbetaling -> {
            HttpStatusCode.BadRequest.errorJson(
                "Stans vil føre til feilutbetalinger",
                "stans_fører_til_feilutbetaling",
            )
        }
        FeilVedKryssjekkAvTidslinjerOgSimulering.Stans.SimuleringInneholderUtbetalinger -> {
            HttpStatusCode.InternalServerError.errorJson(
                "Stans inneholder måneder med utbetaling",
                "stans_inneholder_måneder_til_utbetaling",
            )
        }
    }
}
