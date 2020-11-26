package no.nav.su.se.bakover.client.oppdrag

import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import java.time.LocalDate
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

fun LocalDate.toOppdragDate() = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    .withZone(zoneIdOslo).format(this)

fun Tidspunkt.toOppdragTimestamp() = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
    .withZone(zoneIdOslo).format(this)

fun Tidspunkt.toAvstemmingsdatoFormat() = DateTimeFormatter.ofPattern("yyyyMMddHH")
    .withZone(zoneIdOslo).format(this)

fun Tidspunkt.toOppgaveFormat() = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    .withZone(zoneIdOslo).format(this)
