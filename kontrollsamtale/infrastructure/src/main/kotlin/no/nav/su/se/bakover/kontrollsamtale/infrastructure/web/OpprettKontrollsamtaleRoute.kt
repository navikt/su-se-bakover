package no.nav.su.se.bakover.kontrollsamtale.infrastructure.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.opprett.KanIkkeOppretteKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.opprett.OpprettKontrollsamtaleCommand

fun Route.opprettKontrollsamtaleRoute(
    kontrollsamtaleService: KontrollsamtaleService,
) {
    data class Body(
        val innkallingsmåned: String,
    )

    post("/saker/{sakId}/kontrollsamtaler") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBody<Body> { body ->
                    val opprettKontrollsamtaleCommand = OpprettKontrollsamtaleCommand(
                        sakId = sakId,
                        saksbehandler = call.suUserContext.saksbehandler,
                        innkallingsmåned = Måned.parse(body.innkallingsmåned)!!,
                    )
                    kontrollsamtaleService.opprettKontrollsamtale(opprettKontrollsamtaleCommand).fold(
                        {
                            call.svar(
                                when (it) {
                                    is KanIkkeOppretteKontrollsamtale.IngenStønadsperiode -> Feilresponser.fantIkkeGjeldendeStønadsperiode
                                    is KanIkkeOppretteKontrollsamtale.InnkallingsmånedMåVæreEtterNåværendeMåned -> HttpStatusCode.NotFound.errorJson(
                                        "Innkallingsmåned må være etter nåværende måned",
                                        "innkallingsmåned_må_være_etter_nåværende_måned",
                                    )

                                    is KanIkkeOppretteKontrollsamtale.InnkallingsmånedUtenforStønadsperiode -> HttpStatusCode.NotFound.errorJson(
                                        "Innkallingsmåned utenfor stønadsperiode",
                                        "innkallingsmåned_utenfor_stønadsperiode",
                                    )

                                    is KanIkkeOppretteKontrollsamtale.UgyldigInnkallingsmåned -> HttpStatusCode.NotFound.errorJson(
                                        "Ugyldig innkallingsmåned",
                                        "ugyldig_innkallingsmåned",
                                    )
                                },
                            )
                        },
                        { call.svar(Resultat.json(HttpStatusCode.OK, it.toJson())) },
                    )
                }
            }
        }
    }
}
