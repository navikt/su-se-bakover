package no.nav.su.se.bakover.client.oppdrag

import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data object OppdragDefaults {
    const val SAKSBEHANDLER_ID = "SU"
    const val KODE_KOMPONENT = "SU"
    val utbetalingsfrekvens = UtbetalingRequest.Utbetalingsfrekvens.MND
    val datoOppdragGjelderFom = LocalDate.EPOCH.toOppdragDate()
    val oppdragsenhet =
        UtbetalingRequest.OppdragsEnhet(
            enhet = "8020",
            typeEnhet = "BOS",
            datoEnhetFom = LocalDate.EPOCH.toOppdragDate(),
        )
}

data object OppdragslinjeDefaults {
    const val SAKSBEHANDLER_ID = "SU"
    val fradragEllerTillegg = UtbetalingRequest.Oppdragslinje.FradragTillegg.TILLEGG
    val typeSats = UtbetalingRequest.Oppdragslinje.TypeSats.MND
}

fun LocalDate.toOppdragDate(): String = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    .withZone(zoneIdOslo).format(this)

fun Tidspunkt.toOppdragTimestamp(): String = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
    .withZone(zoneIdOslo).format(this)

fun Tidspunkt.toAvstemmingsdatoFormat(): String = DateTimeFormatter.ofPattern("yyyyMMddHH")
    .withZone(zoneIdOslo).format(this)
