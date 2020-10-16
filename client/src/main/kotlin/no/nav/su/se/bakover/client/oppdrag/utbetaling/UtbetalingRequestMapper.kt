package no.nav.su.se.bakover.client.oppdrag.utbetaling

import no.nav.su.se.bakover.client.oppdrag.OppdragDefaults
import no.nav.su.se.bakover.client.oppdrag.OppdragslinjeDefaults
import no.nav.su.se.bakover.client.oppdrag.toOppdragDate
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.domain.oppdrag.OversendelseTilOppdrag

internal fun toUtbetalingRequest(
    tilOppdrag: OversendelseTilOppdrag
): UtbetalingRequest {
    return UtbetalingRequest(
        oppdragRequest = UtbetalingRequest.OppdragRequest(
            kodeAksjon = UtbetalingRequest.KodeAksjon.UTBETALING, // Kodeaksjon brukes ikke av simulering
            kodeEndring = if (tilOppdrag.utbetaling.erFørstegangsUtbetaling()) UtbetalingRequest.KodeEndring.NY else UtbetalingRequest.KodeEndring.ENDRING,
            kodeFagomraade = OppdragDefaults.KODE_FAGOMRÅDE,
            fagsystemId = tilOppdrag.utbetaling.oppdragId.toString(),
            utbetFrekvens = OppdragDefaults.utbetalingsfrekvens,
            oppdragGjelderId = tilOppdrag.utbetaling.fnr.toString(),
            saksbehId = OppdragDefaults.SAKSBEHANDLER_ID,
            datoOppdragGjelderFom = OppdragDefaults.datoOppdragGjelderFom,
            oppdragsEnheter = OppdragDefaults.oppdragsenheter,
            avstemming = UtbetalingRequest.Avstemming( // Avstemming brukes ikke av simulering
                nokkelAvstemming = tilOppdrag.avstemmingsnøkkel.toString(),
                tidspktMelding = tilOppdrag.avstemmingsnøkkel.opprettet.toOppdragTimestamp(),
                kodeKomponent = OppdragDefaults.KODE_KOMPONENT
            ),
            oppdragslinjer = tilOppdrag.utbetaling.utbetalingslinjer.map {
                UtbetalingRequest.Oppdragslinje(
                    kodeEndringLinje = OppdragslinjeDefaults.kodeEndring,
                    delytelseId = it.id.toString(),
                    kodeKlassifik = OppdragslinjeDefaults.KODE_KLASSIFIK,
                    datoVedtakFom = it.fraOgMed.toOppdragDate(),
                    datoVedtakTom = it.tilOgMed.toOppdragDate(),
                    sats = it.beløp.toString(),
                    fradragTillegg = OppdragslinjeDefaults.fradragEllerTillegg,
                    typeSats = OppdragslinjeDefaults.typeSats,
                    brukKjoreplan = OppdragslinjeDefaults.BRUK_KJOREPLAN,
                    saksbehId = OppdragslinjeDefaults.SAKSBEHANDLER_ID,
                    utbetalesTilId = tilOppdrag.utbetaling.fnr.toString(),
                    refDelytelseId = it.forrigeUtbetalingslinjeId?.toString(),
                    refFagsystemId = it.forrigeUtbetalingslinjeId?.let { tilOppdrag.utbetaling.oppdragId.toString() },
                    attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant(tilOppdrag.utbetaling.behandler.navIdent))
                )
            }
        )
    )
}
