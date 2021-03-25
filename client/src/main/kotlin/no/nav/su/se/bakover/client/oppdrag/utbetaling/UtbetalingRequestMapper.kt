package no.nav.su.se.bakover.client.oppdrag.utbetaling

import no.nav.su.se.bakover.client.oppdrag.OppdragDefaults
import no.nav.su.se.bakover.client.oppdrag.OppdragslinjeDefaults
import no.nav.su.se.bakover.client.oppdrag.toOppdragDate
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje

internal fun toUtbetalingRequest(
    utbetaling: Utbetaling,
): UtbetalingRequest {
    return UtbetalingRequest(
        oppdragRequest = UtbetalingRequest.OppdragRequest(
            kodeAksjon = UtbetalingRequest.KodeAksjon.UTBETALING, // Kodeaksjon brukes ikke av simulering
            kodeEndring = when (utbetaling.type) {
                Utbetaling.UtbetalingsType.NY -> {
                    if (utbetaling.erFørstegangsUtbetaling()) UtbetalingRequest.KodeEndring.NY else UtbetalingRequest.KodeEndring.ENDRING
                }
                Utbetaling.UtbetalingsType.STANS, Utbetaling.UtbetalingsType.GJENOPPTA, Utbetaling.UtbetalingsType.OPPHØR -> {
                    UtbetalingRequest.KodeEndring.ENDRING
                }
            },
            kodeFagomraade = OppdragDefaults.KODE_FAGOMRÅDE,
            fagsystemId = utbetaling.saksnummer.toString(),
            utbetFrekvens = OppdragDefaults.utbetalingsfrekvens,
            oppdragGjelderId = utbetaling.fnr.toString(),
            saksbehId = OppdragDefaults.SAKSBEHANDLER_ID,
            datoOppdragGjelderFom = OppdragDefaults.datoOppdragGjelderFom,
            oppdragsEnheter = OppdragDefaults.oppdragsenheter,
            avstemming = UtbetalingRequest.Avstemming(
                // Avstemming brukes ikke av simulering
                nokkelAvstemming = utbetaling.avstemmingsnøkkel.toString(),
                tidspktMelding = utbetaling.avstemmingsnøkkel.opprettet.toOppdragTimestamp(),
                kodeKomponent = OppdragDefaults.KODE_KOMPONENT,
            ),
            oppdragslinjer = utbetaling.utbetalingslinjer.map {
                when (it) {
                    is Utbetalingslinje.Endring -> {
                        UtbetalingRequest.Oppdragslinje(
                            kodeStatusLinje = when (it.statusendring.status) {
                                Utbetalingslinje.LinjeStatus.OPPHØRT -> UtbetalingRequest.Oppdragslinje.KodeStatusLinje.OPPHØR
                            },
                            datoStatusFom = it.statusendring.fraOgMed.toOppdragDate(),
                            kodeEndringLinje = UtbetalingRequest.Oppdragslinje.KodeEndringLinje.ENDRING,
                            delytelseId = it.id.toString(),
                            kodeKlassifik = OppdragslinjeDefaults.KODE_KLASSIFIK,
                            datoVedtakFom = it.fraOgMed.toOppdragDate(),
                            datoVedtakTom = it.tilOgMed.toOppdragDate(),
                            sats = it.beløp.toString(),
                            fradragTillegg = OppdragslinjeDefaults.fradragEllerTillegg,
                            typeSats = OppdragslinjeDefaults.typeSats,
                            brukKjoreplan = OppdragslinjeDefaults.BRUK_KJOREPLAN,
                            saksbehId = OppdragslinjeDefaults.SAKSBEHANDLER_ID,
                            utbetalesTilId = utbetaling.fnr.toString(),
                            refDelytelseId = null,
                            refFagsystemId = null,
                            attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant(utbetaling.behandler.navIdent)),
                        )
                    }
                    is Utbetalingslinje.Ny -> {
                        UtbetalingRequest.Oppdragslinje(
                            kodeStatusLinje = null,
                            datoStatusFom = null,
                            kodeEndringLinje = UtbetalingRequest.Oppdragslinje.KodeEndringLinje.NY,
                            delytelseId = it.id.toString(),
                            kodeKlassifik = OppdragslinjeDefaults.KODE_KLASSIFIK,
                            datoVedtakFom = it.fraOgMed.toOppdragDate(),
                            datoVedtakTom = it.tilOgMed.toOppdragDate(),
                            sats = it.beløp.toString(),
                            fradragTillegg = OppdragslinjeDefaults.fradragEllerTillegg,
                            typeSats = OppdragslinjeDefaults.typeSats,
                            brukKjoreplan = OppdragslinjeDefaults.BRUK_KJOREPLAN,
                            saksbehId = OppdragslinjeDefaults.SAKSBEHANDLER_ID,
                            utbetalesTilId = utbetaling.fnr.toString(),
                            refDelytelseId = it.forrigeUtbetalingslinjeId?.toString(),
                            refFagsystemId = it.forrigeUtbetalingslinjeId?.let { utbetaling.saksnummer.toString() },
                            attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant(utbetaling.behandler.navIdent)),
                        )
                    }
                }
            },
        ),
    )
}
