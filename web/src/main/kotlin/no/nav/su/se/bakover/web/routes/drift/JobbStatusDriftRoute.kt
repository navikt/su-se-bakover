package no.nav.su.se.bakover.web.routes.drift

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.job.JobbKjøring
import no.nav.su.se.bakover.common.domain.job.JobbKjøringRepo
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.serialize

internal fun Route.jobbStatusDriftRoute(
    jobbKjøringRepo: JobbKjøringRepo,
) {
    get("$DRIFT_PATH/jobber/status") {
        authorize(Brukerrolle.Drift) {
            val sisteKjøringer = jobbKjøringRepo.hentSistePerJobb()
            call.svar(
                Resultat.json(
                    HttpStatusCode.OK,
                    serialize(sisteKjøringer.map { it.toJson() }),
                ),
            )
        }
    }
}

private fun JobbKjøring.toJson(): JobbKjøringJson = JobbKjøringJson(
    id = id.toString(),
    jobbNavn = jobbNavn,
    status = status.name,
    startetTidspunkt = startetTidspunkt.toString(),
    ferdigTidspunkt = ferdigTidspunkt?.toString(),
    feilmelding = feilmelding,
    intervallSekunder = intervall.seconds,
)

data class JobbKjøringJson(
    val id: String,
    val jobbNavn: String,
    val status: String,
    val startetTidspunkt: String,
    val ferdigTidspunkt: String?,
    val feilmelding: String?,
    val intervallSekunder: Long,
)
