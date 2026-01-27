package no.nav.su.se.bakover.web.routes.drift

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import java.time.LocalDate
import java.time.YearMonth

internal fun Route.kontrollsamtalerDriftRoute(
    service: KontrollsamtaleService,
) {
    get("$DRIFT_PATH/kontrollsamtaler") {
        authorize(Brukerrolle.Drift) {
            val nå = YearMonth.now().atEndOfMonth()
            val nesteMåned = nå.plusMonths(1)
            val innkalliger = service.hentInnkalteKontrollsamtaleForDrift(nesteMåned)
            val antall = KontrollsamtaleDrift(
                listOf(
                    innkalliger.antallPerFrist(nå),
                    innkalliger.antallPerFrist(nesteMåned),
                ),
            )
            call.svar(Resultat.json(HttpStatusCode.OK, serialize(antall)))
        }
    }
}

data class KontrollsamtaleDrift(
    val kontrollsamtaleAntall: List<KontrollsamtaleAntall>,
)

data class KontrollsamtaleAntall(
    val frist: LocalDate,
    val antall: Int,
)

fun List<Kontrollsamtale>.antallPerFrist(frist: LocalDate) = KontrollsamtaleAntall(
    frist = frist,
    antall = filter { it.frist == frist }.size,
)
