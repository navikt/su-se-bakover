package no.nav.su.se.bakover.web.routes.drift

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.service.statistikk.KunneIkkeErstatteSakStatistikk
import no.nav.su.se.bakover.service.statistikk.SakStatistikkBigQueryService
import org.slf4j.LoggerFactory
import java.time.LocalDate

internal fun Route.sakStatistikkRoutes(
    service: SakStatistikkBigQueryService,
) {
    val log = LoggerFactory.getLogger("SakStatistikkRoute")

    post("$DRIFT_PATH/statistikk/sak") {
        data class Body(
            val fraOgMed: LocalDate,
            val tilOgMed: LocalDate,
        )
        authorize(Brukerrolle.Drift) {
            call.withBody<Body> {
                withContext(Dispatchers.IO) {
                    service.lastTilBigQuery(it.fraOgMed, it.tilOgMed)
                }
                call.svar(Resultat.okJson())
            }
        }
    }

    post("$DRIFT_PATH/statistikk/sak/erstatt") {
        data class Body(val sekvensIder: List<Long>)

        authorize(Brukerrolle.Drift) {
            call.withBody<Body> { body ->
                val resultat = withContext(Dispatchers.IO) {
                    service.erstattSakStatistikk(body.sekvensIder)
                }.fold(
                    ifLeft = { it.tilResultat() },
                    ifRight = {
                        Resultat.json(
                            httpCode = HttpStatusCode.OK,
                            json = serialize(it),
                        )
                    },
                )
                call.svar(resultat)
            }
        }
    }

    post("$DRIFT_PATH/statistikk/sak/erstatt/forhandsvis") {
        data class Body(val sekvensIder: List<Long>)

        authorize(Brukerrolle.Drift) {
            call.withBody<Body> { body ->
                try {
                    val forhåndsvisning = withContext(Dispatchers.IO) {
                        service.forhåndsvisErstattSakStatistikk(body.sekvensIder)
                    }
                    val resultat = forhåndsvisning.fold(
                        ifLeft = { it.tilResultat() },
                        ifRight = {
                            Resultat.json(
                                httpCode = HttpStatusCode.OK,
                                json = serialize(it),
                            )
                        },
                    )
                    call.svar(resultat)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("Kunne ikke hente grunnlaget for forhåndsvisning av sakstatistikk", e)
                    call.svar(
                        HttpStatusCode.InternalServerError.errorJson(
                            message = "Kunne ikke hente grunnlaget for forhåndsvisningen.",
                            code = "forhandsvisning_sak_statistikk_feilet",
                        ),
                    )
                }
            }
        }
    }
}

private fun KunneIkkeErstatteSakStatistikk.tilResultat(): Resultat = when (this) {
    KunneIkkeErstatteSakStatistikk.IngenSekvensIder -> HttpStatusCode.BadRequest.errorJson(
        message = "Listen med sekvens-ID-er kan ikke være tom",
        code = "ingen_sekvens_ider",
    )
    is KunneIkkeErstatteSakStatistikk.ForMangeSekvensIder -> HttpStatusCode.BadRequest.errorJson(
        message = "Det kan maksimalt sendes $maksimaltAntall sekvens-ID-er per kall",
        code = "for_mange_sekvens_ider",
    )
    is KunneIkkeErstatteSakStatistikk.UgyldigeSekvensIder -> HttpStatusCode.BadRequest.errorJson(
        message = "Alle sekvens-ID-er må være positive. Ugyldige ID-er: ${sekvensIder.sorted()}",
        code = "ugyldige_sekvens_ider",
    )
    is KunneIkkeErstatteSakStatistikk.DuplikateSekvensIder -> HttpStatusCode.BadRequest.errorJson(
        message = "Requesten inneholder duplikate sekvens-ID-er: ${sekvensIder.sorted()}",
        code = "duplikate_sekvens_ider",
    )
    is KunneIkkeErstatteSakStatistikk.AvvikISakStatistikk -> HttpStatusCode.Conflict.errorJson(
        message = "Avvik i sak_statistikk. Manglende sekvens-ID-er: ${manglendeSekvensIder.sorted()}. " +
            "Ikke-unike sekvens-ID-er: ${ikkeUnikeSekvensIder.sorted()}. " +
            "Uventede sekvens-ID-er: ${uventedeSekvensIder.sorted()}.",
        code = "avvik_i_sak_statistikk",
    )
    is KunneIkkeErstatteSakStatistikk.UgyldigTilstandIBigQuery -> HttpStatusCode.Conflict.errorJson(
        message = "BigQuery har $antallRaderIBigQuery fysiske rader for $antallForespurte forespurte sekvens-ID-er. " +
            "Manglende sekvens-ID-er: ${manglendeSekvensIder.sorted()}. " +
            "Ikke-unike sekvens-ID-er: ${ikkeUnikeSekvensIder.sorted()}. " +
            "Uventede sekvens-ID-er: ${uventedeSekvensIder.sorted()}.",
        code = "ugyldig_tilstand_i_bigquery",
    )
}
