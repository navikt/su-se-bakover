package no.nav.su.se.bakover.client.oppdrag.utbetaling

import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun toUtbetalingRequest(oppdrag: Oppdrag, utbetaling: Utbetaling, oppdragGjelder: Fnr): UtbetalingRequest {
    return UtbetalingRequest(
        oppdragRequest = UtbetalingRequest.OppdragRequest(
            kodeAksjon = UtbetalingRequest.KodeAksjon.UTBETALING, // Kodeaksjon brukes ikke av simulering
            kodeEndring = if (oppdrag.sisteUtbetaling() != null) UtbetalingRequest.KodeEndring.ENDRING else UtbetalingRequest.KodeEndring.NY,
            kodeFagomraade = OppdragDefaults.KODE_FAGOMRÅDE,
            fagsystemId = oppdrag.id.toString(),
            utbetFrekvens = OppdragDefaults.utbetalingsfrekvens,
            oppdragGjelderId = oppdragGjelder.fnr,
            saksbehId = OppdragDefaults.SAKSBEHANDLER_ID,
            datoOppdragGjelderFom = OppdragDefaults.datoOppdragGjelderFom,
            oppdragsEnheter = OppdragDefaults.oppdragsenheter,
            avstemming = UtbetalingRequest.Avstemming( // Avstemming brukes ikke av simulering
                nokkelAvstemming = utbetaling.id.toString(),
                tidspktMelding = utbetaling.opprettet.toOppdragTimestamp(),
                kodeKomponent = OppdragDefaults.AVSTEMMING_KODE_KOMPONENT
            ),
            oppdragslinjer = utbetaling.utbetalingslinjer.map {
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
                    utbetalesTilId = oppdragGjelder.fnr,
                    refDelytelseId = it.forrigeUtbetalingslinjeId?.toString(),
                    refFagsystemId = it.forrigeUtbetalingslinjeId?.let { oppdrag.id.toString() }
                )
            }
        )
    )
}

private object OppdragDefaults {
    const val KODE_FAGOMRÅDE = "SUUFORE"
    const val SAKSBEHANDLER_ID = "SU"
    val utbetalingsfrekvens = UtbetalingRequest.Utbetalingsfrekvens.MND
    val datoOppdragGjelderFom = LocalDate.EPOCH.toOppdragDate()
    const val AVSTEMMING_KODE_KOMPONENT = "SUUFORE"
    val oppdragsenheter = listOf(
        UtbetalingRequest.OppdragsEnhet(
            enhet = "8020",
            typeEnhet = "BOS",
            datoEnhetFom = LocalDate.EPOCH.toOppdragDate()
        )
    )
}

private object OppdragslinjeDefaults {
    val kodeEndring = UtbetalingRequest.Oppdragslinje.KodeEndringLinje.NY
    const val KODE_KLASSIFIK = "SUUFORE"
    val fradragEllerTillegg = UtbetalingRequest.Oppdragslinje.FradragTillegg.TILLEGG
    const val SAKSBEHANDLER_ID = "SU"
    val typeSats = UtbetalingRequest.Oppdragslinje.TypeSats.MND
    const val BRUK_KJOREPLAN = "N"
}

private val zoneId = ZoneId.of("Europe/Oslo")

fun LocalDate.toOppdragDate() = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    .withZone(zoneId).format(this)

fun Instant.toOppdragTimestamp() = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
    .withZone(zoneId).format(this)
