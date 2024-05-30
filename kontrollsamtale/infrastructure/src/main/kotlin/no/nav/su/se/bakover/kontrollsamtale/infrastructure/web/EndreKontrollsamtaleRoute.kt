package no.nav.su.se.bakover.kontrollsamtale.infrastructure.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withKontrollsamtaleId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.endre.EndreKontrollsamtaleCommand
import java.util.UUID

fun Route.endreKontrollsamtaleRoute(
    kontrollsamtaleService: KontrollsamtaleService,
) {
    /**
     * @param journalpostId Gjelder for kontrollnotatet til veileder.
     */
    data class Body(
        val innkallingsmåned: String?,
        val status: String?,
        val journalpostId: String?,
    ) {
        fun toKontrollsamtaleCommand(
            sakId: UUID,
            saksbehandler: NavIdentBruker.Saksbehandler,
            kontrollsamtaleId: UUID,
        ): EndreKontrollsamtaleCommand {
            return EndreKontrollsamtaleCommand(
                sakId = sakId,
                kontrollsamtaleId = kontrollsamtaleId,
                saksbehandler = saksbehandler,
                nyInnkallingsmåned = innkallingsmåned?.let { Måned.parse(it) },
                nyStatus = status?.let {
                    when (it) {
                        "INNKALT" -> EndreKontrollsamtaleCommand.EndreStatusTil.Innkalt
                        "IKKE_MØTT_INNEN_FRIST" -> EndreKontrollsamtaleCommand.EndreStatusTil.IkkeMøttInnenFrist
                        "GJENNOMFØRT" -> EndreKontrollsamtaleCommand.EndreStatusTil.Gjennomført(
                            journalpostId?.let {
                                JournalpostId(it)
                            }!!,
                        )

                        else -> throw IllegalArgumentException("Ukjent status: $it")
                    }
                },
            )
        }
    }

    post("/saker/{sakId}/kontrollsamtaler") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withKontrollsamtaleId { kontrollsamtaleId ->
                    call.withBody<Body> { body ->
                        kontrollsamtaleService.endreKontrollsamtale(
                            body.toKontrollsamtaleCommand(
                                sakId = sakId,
                                saksbehandler = call.suUserContext.saksbehandler,
                                kontrollsamtaleId = kontrollsamtaleId,
                            ),
                        ).fold(
                            {
                                TODO()
                            },
                            { call.svar(Resultat.json(HttpStatusCode.OK, it.toJson())) },
                        )
                    }
                }
            }
        }
    }
}
