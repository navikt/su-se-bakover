package no.nav.su.se.bakover.web.external

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.web.features.authorize
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal const val frikortPath = "/frikort"

@KtorExperimentalAPI
internal fun Route.frikortRoutes(
    søknadsbehandlingService: SøknadsbehandlingService
) {
    // val log = LoggerFactory.getLogger(this::class.java)

    // authorize(Brukerrolle.Saksbehandler) {
    get("$frikortPath/sdf") {
        val aktivDato = call.parameters["aktivDato"] // YYYY-MM-DD  TODO legge inn hyggeligere feilmelding
            ?.let { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }
            ?: LocalDate.now()
        val aktiveBehandlinger = søknadsbehandlingService.hentAktiveBehandlinger(
            SøknadsbehandlingService.HentAktiveRequest(
                aktivDato
            )
        ).mapLeft {
            throw IllegalArgumentException("hallo")
        }.map {
            it.map {
                FrikortJson(
                    fnr = it.fnr.toString(),
                    fraOgMed = it.beregning.getPeriode().getFraOgMed().toString(),
                    tilOgMed = it.beregning.getPeriode().getTilOgMed().toString()
                )
            }
        }
        call.respond(aktiveBehandlinger)
    }
    // }
}

@KtorExperimentalAPI
internal fun Route.frikortVedtakRoutes(
    vedtakService: VedtakService
) {
    // val log = LoggerFactory.getLogger(this::class.java)

    authorize(Brukerrolle.Drift) {
        get("$frikortPath") {
            val aktivDato = call.parameters["aktivDato"] // YYYY-MM-DD  TODO legge inn hyggeligere feilmelding
                ?.let { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }
                ?: LocalDate.now()
            val aktiveBehandlinger = vedtakService.hentAktive(aktivDato).map {
                FrikortJson(
                    fnr = it.behandling.fnr.toString(),
                    fraOgMed = it.beregning.getPeriode().getFraOgMed().toString(),
                    tilOgMed = it.beregning.getPeriode().getTilOgMed().toString()
                )
            }
            call.respond(aktiveBehandlinger)
        }
    }
}

data class FrikortJson(
    val fnr: String,
    val fraOgMed: String,
    val tilOgMed: String
)
