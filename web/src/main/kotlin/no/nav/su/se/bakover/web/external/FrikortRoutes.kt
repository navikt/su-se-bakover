package no.nav.su.se.bakover.web.external

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

internal const val frikortPath = "/frikort"
internal val formatter = DateTimeFormatter.ofPattern("yyyy-MM")

@KtorExperimentalAPI
internal fun Route.frikortVedtakRoutes(
    vedtakService: VedtakService,
    clock: Clock
) {
    get("$frikortPath/{aktivDato?}") {
        val aktivDato = call.parameters["aktivDato"] // YYYY-MM  2021-02
            ?.let { YearMonth.parse(it, formatter).atDay(1) }
            ?: Tidspunkt.now(clock).toLocalDate(zoneIdOslo)
        val aktiveBehandlinger = vedtakService.hentAktive(aktivDato).map {
            it.behandling.fnr
        }
        call.respond(object { val dato = aktivDato.toFrikortFormat(); val fnr = aktiveBehandlinger })
    }
}

fun LocalDate.toFrikortFormat(): String = formatter
    .withZone(zoneIdOslo).format(this)
