package no.nav.su.se.bakover.client.oppdrag.utbetaling

import no.nav.su.se.bakover.client.oppdrag.OppdragDefaults
import no.nav.su.se.bakover.client.oppdrag.OppdragslinjeDefaults
import no.nav.su.se.bakover.client.oppdrag.toOppdragDate
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import java.time.Instant

internal fun toUtbetalingRequest(nyUtbetaling: NyUtbetaling, tidspunkt: Instant = Instant.now()): UtbetalingRequest {
    return UtbetalingRequest(
        oppdragRequest = UtbetalingRequest.OppdragRequest(
            kodeAksjon = UtbetalingRequest.KodeAksjon.UTBETALING, // Kodeaksjon brukes ikke av simulering
            kodeEndring = if (nyUtbetaling.oppdrag.sisteOversendteUtbetaling() != null) UtbetalingRequest.KodeEndring.ENDRING else UtbetalingRequest.KodeEndring.NY,
            kodeFagomraade = OppdragDefaults.KODE_FAGOMRÅDE,
            fagsystemId = nyUtbetaling.oppdrag.id.toString(),
            utbetFrekvens = OppdragDefaults.utbetalingsfrekvens,
            oppdragGjelderId = nyUtbetaling.utbetaling.fnr.toString(),
            saksbehId = OppdragDefaults.SAKSBEHANDLER_ID,
            datoOppdragGjelderFom = OppdragDefaults.datoOppdragGjelderFom,
            oppdragsEnheter = OppdragDefaults.oppdragsenheter,
            avstemming = UtbetalingRequest.Avstemming( // Avstemming brukes ikke av simulering
                nokkelAvstemming = "${Avstemmingsnøkkel.generer(tidspunkt)}",
                tidspktMelding = tidspunkt.toOppdragTimestamp(), // TODO prevent timestamp rolling over to next day
                kodeKomponent = OppdragDefaults.KODE_KOMPONENT
            ),
            oppdragslinjer = nyUtbetaling.utbetaling.utbetalingslinjer.map {
                UtbetalingRequest.Oppdragslinje(
                    kodeEndringLinje = OppdragslinjeDefaults.kodeEndring,
                    delytelseId = it.id.toString(),
                    kodeKlassifik = OppdragslinjeDefaults.KODE_KLASSIFIK,
                    datoVedtakFom = it.fom.toOppdragDate(),
                    datoVedtakTom = it.tom.toOppdragDate(),
                    sats = it.beløp.toString(),
                    fradragTillegg = OppdragslinjeDefaults.fradragEllerTillegg,
                    typeSats = OppdragslinjeDefaults.typeSats,
                    brukKjoreplan = OppdragslinjeDefaults.BRUK_KJOREPLAN,
                    saksbehId = OppdragslinjeDefaults.SAKSBEHANDLER_ID,
                    utbetalesTilId = nyUtbetaling.utbetaling.fnr.toString(),
                    refDelytelseId = it.forrigeUtbetalingslinjeId?.toString(),
                    refFagsystemId = it.forrigeUtbetalingslinjeId?.let { nyUtbetaling.oppdrag.id.toString() },
                    attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant(nyUtbetaling.attestant.id))
                )
            }
        )
    )
}
