package no.nav.su.se.bakover.client.oppdrag

import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object OppdragDefaults {
    const val KODE_FAGOMRÅDE = "SUUFORE"
    const val SAKSBEHANDLER_ID = "SU"
    const val KODE_KOMPONENT = "SU"
    val utbetalingsfrekvens = UtbetalingRequest.Utbetalingsfrekvens.MND
    val datoOppdragGjelderFom = LocalDate.EPOCH.toOppdragDate()
    val oppdragsenheter = listOf(
        UtbetalingRequest.OppdragsEnhet(
            enhet = "8020",
            typeEnhet = "BOS",
            datoEnhetFom = LocalDate.EPOCH.toOppdragDate()
        )
    )
}

object OppdragslinjeDefaults {
    const val KODE_KLASSIFIK = "SUUFORE"
    const val SAKSBEHANDLER_ID = "SU"
    const val BRUK_KJOREPLAN = "N"
    val kodeEndring = UtbetalingRequest.Oppdragslinje.KodeEndringLinje.NY
    val fradragEllerTillegg = UtbetalingRequest.Oppdragslinje.FradragTillegg.TILLEGG
    val typeSats = UtbetalingRequest.Oppdragslinje.TypeSats.MND
}

private val zoneId = ZoneId.of("Europe/Oslo")

fun LocalDate.toOppdragDate() = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    .withZone(zoneId).format(this)

fun Instant.toOppdragTimestamp() = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
    .withZone(zoneId).format(this)
