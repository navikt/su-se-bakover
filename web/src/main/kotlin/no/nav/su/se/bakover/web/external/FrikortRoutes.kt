package no.nav.su.se.bakover.web.external

import arrow.core.Either
import arrow.core.getOrHandle
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.svar
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

internal const val frikortPath = "/frikort"
internal val formatter = DateTimeFormatter.ofPattern("yyyy-MM")

internal fun Route.frikortVedtakRoutes(
    vedtakService: VedtakService,
    clock: Clock,
) {
    fun hentDato(dato: String): Either<Resultat, LocalDate> {
        return Either.catch { YearMonth.parse(dato, formatter).atDay(1) }
            .mapLeft {
                HttpStatusCode.BadRequest.errorJson(
                    "Ugyldig dato - dato må være på format YYYY-MM",
                    "ugyldig_datoformat",
                )
            }
    }

    // Her kan man få kode 6 og kode 7...
    get("$frikortPath/{aktivDato?}") {
        val aktivDato = call.parameters["aktivDato"] // YYYY-MM  2021-02
            ?.let {
                hentDato(it).getOrHandle {
                    call.svar(it)
                    return@get
                }
            }
            ?: Tidspunkt.now(clock).toLocalDate(zoneIdOslo)
        val aktiveBehandlinger = vedtakService.hentAktiveFnr(aktivDato)
        call.svar(
            Resultat.json(
                HttpStatusCode.OK,
                serialize(
                    object {
                        val dato = aktivDato.toFrikortFormat()
                        val fnr = aktiveBehandlinger
                    },
                ),
            ),
        )
    }
}

fun LocalDate.toFrikortFormat(): String = formatter
    .withZone(zoneIdOslo).format(this)
