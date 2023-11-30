package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.kryssjekkTidslinjeSimuleringFeilet
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.kunneIkkeUtbetale
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.domain.oppdrag.simulering.KontrollsimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.KryssjekkAvTidslinjeOgSimuleringFeilet
import no.nav.su.se.bakover.domain.søknadsbehandling.StøtterIkkeOverlappendeStønadsperioder
import no.nav.su.se.bakover.domain.søknadsbehandling.simuler.KunneIkkeSimulereBehandling
import økonomi.domain.simulering.ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.UtbetalingFeilet

internal fun UtbetalingFeilet.tilResultat(): Resultat {
    return when (this) {
        UtbetalingFeilet.Protokollfeil -> kunneIkkeUtbetale
    }
}

internal fun KontrollsimuleringFeilet.tilResultat(): Resultat {
    return when (this) {
        is KontrollsimuleringFeilet.Forskjeller -> this.underliggende.tilResultat()
        is KontrollsimuleringFeilet.KunneIkkeSimulere -> this.underliggende.tilResultat()
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

internal fun KunneIkkeSimulereBehandling.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeSimulereBehandling.KunneIkkeSimulere -> this.feil.tilResultat()
        is KunneIkkeSimulereBehandling.UgyldigTilstand -> {
            Feilresponser.ugyldigTilstand(this.fra, this.til)
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
                // TODO jah: Kan kanskje returnere denne dataen?
                is ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode.UtbetalingslinjeVarIkke0 -> kryssjekkTidslinjeSimuleringFeilet
                is ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode.UlikPeriode -> kryssjekkTidslinjeSimuleringFeilet
                is ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode.UliktBeløp -> kryssjekkTidslinjeSimuleringFeilet
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
}
