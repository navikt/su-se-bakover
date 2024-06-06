package no.nav.su.se.bakover.kontrollsamtale.infrastructure.web

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withKontrollsamtaleId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.innkallingsmåned.KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.innkallingsmåned.OppdaterInnkallingsmånedPåKontrollsamtaleCommand
import java.util.UUID

fun Route.oppdaterInnkallingsmånedPåKontrollsamtale(
    kontrollsamtaleService: KontrollsamtaleService,
) {
    data class MyEx(val feil: Resultat) : RuntimeException()
    data class Body(
        val innkallingsmåned: String,
    ) {
        fun toKontrollsamtaleCommand(
            sakId: UUID,
            saksbehandler: NavIdentBruker.Saksbehandler,
            kontrollsamtaleId: UUID,
        ): Either<Resultat, OppdaterInnkallingsmånedPåKontrollsamtaleCommand> {
            return OppdaterInnkallingsmånedPåKontrollsamtaleCommand(
                sakId = sakId,
                kontrollsamtaleId = kontrollsamtaleId,
                saksbehandler = saksbehandler,
                nyInnkallingsmåned = innkallingsmåned.let { Måned.parse(it) }
                    ?: return HttpStatusCode.BadRequest.errorJson(
                        "Ugyldig måned. Forventer måned på formatet 'YYYY-MM'",
                        "ugyldig_måned",
                    ).left(),
            ).right()
        }
    }

    patch("/saker/{sakId}/kontrollsamtaler/{kontrollsamtaleId}/innkallingsmåned") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withKontrollsamtaleId { kontrollsamtaleId ->
                    call.withBody<Body> { body ->
                        body.toKontrollsamtaleCommand(
                            sakId = sakId,
                            saksbehandler = call.suUserContext.saksbehandler,
                            kontrollsamtaleId = kontrollsamtaleId,
                        ).fold(
                            { call.svar(it) },
                            {
                                kontrollsamtaleService.oppdaterInnkallingsmånedPåKontrollsamtale(it).fold(
                                    { call.svar(mapErrorToJsonResultat(it)) },
                                    { call.svar(Resultat.json(HttpStatusCode.OK, it.toJson())) },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun mapErrorToJsonResultat(error: KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale) =
    when (error) {
        is KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale.KanKunOppdatereInnkallingsmånedForPlanlagtInnkalling -> HttpStatusCode.BadRequest.errorJson(
            "Kan kun oppdatere innkallingsmåned når status er planlagt innkalling",
            "kan_kun_oppdatere_innkallingsmåned_for_planlagt_innkalling",
        )

        is KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale.KontrollsamtaleAnnullert -> HttpStatusCode.BadRequest.errorJson(
            "Kontrollsamtale er annullert. Det finnes et eget endepunkt for å annullere kontrollsamtaler.",
            "kontrollsamtale_er_annullert",
        )

        is KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale.InnkallingsmånedMåVæreEtterNåværendeMåned -> HttpStatusCode.BadRequest.errorJson(
            "Innkallingsmåned må være etter nåværende måned",
            "innkallingsmåned_må_være_etter_nåværende_måned",
        )

        is KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale.InnkallingsmånedUtenforStønadsperiode -> HttpStatusCode.BadRequest.errorJson(
            "Innkallingsmåned er utenfor stønadsperiode",
            "innkallingsmåned_er_utenfor_stønadsperiode",
        )

        is KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale.UgyldigInnkallingsmåned -> HttpStatusCode.BadRequest.errorJson(
            "Ugyldig innkallingsmåned",
            "ugyldig_innkallingsmåned",
        )
    }
