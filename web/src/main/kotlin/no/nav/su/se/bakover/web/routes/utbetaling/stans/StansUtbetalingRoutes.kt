package no.nav.su.se.bakover.web.routes.utbetaling.stans

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeStanseUtbetalinger
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withSakId
import java.time.temporal.TemporalAdjusters.firstDayOfNextMonth

internal fun Route.stansutbetalingRoutes(
    utbetalingService: UtbetalingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$sakPath/{sakId}/utbetalinger/stans") {
            call.withSakId { sakId ->
                call.svar(
                    utbetalingService.stansUtbetalinger(
                        sakId = sakId,
                        saksbehandler = call.suUserContext.navIdent.let { Saksbehandler(it) },
                        stansDato = idag().with(firstDayOfNextMonth()),
                    ).fold(
                        {
                            when (it) {
                                KunneIkkeStanseUtbetalinger.FantIkkeSak -> {
                                    NotFound.errorJson(
                                        "Fant ikke sak",
                                        "fant_ikke_sak",
                                    )
                                }
                                KunneIkkeStanseUtbetalinger.SimuleringAvStansFeilet -> {
                                    InternalServerError.errorJson(
                                        "Simulering av stans feilet",
                                        "simulering_av_stans_feilet",
                                    )
                                }
                                KunneIkkeStanseUtbetalinger.SendingAvUtbetalingTilOppdragFeilet -> {
                                    InternalServerError.errorJson(
                                        "Oversendelse til oppdrag feilet",
                                        "oversendelse_til_oppdrag_feilet",
                                    )
                                }
                                KunneIkkeStanseUtbetalinger.KontrollAvSimuleringFeilet -> {
                                    InternalServerError.errorJson(
                                        "Kontroll av simulering feilet",
                                        "kontroll_av_simulering_feilet",
                                    )
                                }
                            }
                        },
                        {
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, null)
                            call.sikkerlogg("Stanser utbetaling på sak $sakId")
                            Resultat.json(OK, serialize(it.toJson()))
                        },
                    ),
                )
            }
        }
    }
}
