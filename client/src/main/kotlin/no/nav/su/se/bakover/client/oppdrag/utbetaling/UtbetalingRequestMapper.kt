package no.nav.su.se.bakover.client.oppdrag.utbetaling

import no.nav.su.se.bakover.client.oppdrag.OppdragDefaults
import no.nav.su.se.bakover.client.oppdrag.OppdragslinjeDefaults
import no.nav.su.se.bakover.client.oppdrag.toOppdragDate
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest.Oppdragslinje.KodeStatusLinje.Companion.tilKjøreplan
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest.Oppdragslinje.KodeStatusLinje.Companion.tilKodeStatusLinje
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest.Oppdragslinje.KodeStatusLinje.Companion.tilUføregrad
import no.nav.su.se.bakover.common.domain.extensions.and
import no.nav.su.se.bakover.domain.oppdrag.avstemming.toFagområde
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalingslinje

private fun Utbetaling.trengerNyttOppdrag(): Boolean = utbetalingslinjer.let { linjer ->
    linjer.any { it.forrigeUtbetalingslinjeId == null }
        // unngå at en eventuell endring av første utbetalingslinje noensinne oppfattes som førstegangsutbetaling
        .and { linjer.filterIsInstance<Utbetalingslinje.Endring>().none { it.forrigeUtbetalingslinjeId == null } }
}

internal fun toUtbetalingRequest(
    utbetaling: Utbetaling,
): UtbetalingRequest {
    return UtbetalingRequest(
        oppdragRequest = UtbetalingRequest.OppdragRequest(
            // Kodeaksjon brukes ikke av simulering
            kodeAksjon = UtbetalingRequest.KodeAksjon.UTBETALING,
            kodeEndring = if (utbetaling.trengerNyttOppdrag()) UtbetalingRequest.KodeEndring.NY else UtbetalingRequest.KodeEndring.ENDRING,
            kodeFagomraade = utbetaling.sakstype.toFagområde().toString(),
            fagsystemId = utbetaling.saksnummer.toString(),
            utbetFrekvens = OppdragDefaults.utbetalingsfrekvens,
            oppdragGjelderId = utbetaling.fnr.toString(),
            saksbehId = OppdragDefaults.SAKSBEHANDLER_ID,
            datoOppdragGjelderFom = OppdragDefaults.datoOppdragGjelderFom,
            oppdragsEnheter = listOf(OppdragDefaults.oppdragsenhet),
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
                            kodeStatusLinje = it.tilKodeStatusLinje(),
                            datoStatusFom = it.periode.fraOgMed.toOppdragDate(),
                            kodeEndringLinje = UtbetalingRequest.Oppdragslinje.KodeEndringLinje.ENDRING,
                            delytelseId = it.id.toString(),
                            // bruker bare fagområde siden vi ikke har flere "sub-ytelser" per fagområde
                            kodeKlassifik = utbetaling.sakstype.toFagområde()
                                .toString(),
                            datoVedtakFom = it.originalFraOgMed().toOppdragDate(),
                            datoVedtakTom = it.originalTilOgMed().toOppdragDate(),
                            sats = it.beløp.toString(),
                            fradragTillegg = OppdragslinjeDefaults.fradragEllerTillegg,
                            typeSats = OppdragslinjeDefaults.typeSats,
                            brukKjoreplan = it.tilKjøreplan(),
                            saksbehId = OppdragslinjeDefaults.SAKSBEHANDLER_ID,
                            utbetalesTilId = utbetaling.fnr.toString(),
                            refDelytelseId = null,
                            refFagsystemId = null,
                            attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant(utbetaling.behandler.navIdent)),
                            grad = it.tilUføregrad(),
                            // Referanse til hvilken utbetaling linjen tilhører
                            utbetalingId = utbetaling.id.toString(),
                        )
                    }

                    is Utbetalingslinje.Ny -> {
                        UtbetalingRequest.Oppdragslinje(
                            kodeStatusLinje = null,
                            datoStatusFom = null,
                            kodeEndringLinje = UtbetalingRequest.Oppdragslinje.KodeEndringLinje.NY,
                            delytelseId = it.id.toString(),
                            // bruker bare fagområde siden vi ikke har flere "sub-ytelser" per fagområde,
                            kodeKlassifik = utbetaling.sakstype.toFagområde()
                                .toString(),
                            datoVedtakFom = it.originalFraOgMed().toOppdragDate(),
                            datoVedtakTom = it.originalTilOgMed().toOppdragDate(),
                            sats = it.beløp.toString(),
                            fradragTillegg = OppdragslinjeDefaults.fradragEllerTillegg,
                            typeSats = OppdragslinjeDefaults.typeSats,
                            brukKjoreplan = it.tilKjøreplan(),
                            saksbehId = OppdragslinjeDefaults.SAKSBEHANDLER_ID,
                            utbetalesTilId = utbetaling.fnr.toString(),
                            refDelytelseId = it.forrigeUtbetalingslinjeId?.toString(),
                            refFagsystemId = it.forrigeUtbetalingslinjeId?.let { utbetaling.saksnummer.toString() },
                            attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant(utbetaling.behandler.navIdent)),
                            grad = it.tilUføregrad(),
                            // Referanse til hvilken utbetaling linjen tilhører
                            utbetalingId = utbetaling.id.toString(),
                        )
                    }
                }
            }.toNonEmptyList(),
        ),
    )
}
