package no.nav.su.se.bakover.kontrollsamtale.infrastructure.web

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withKontrollsamtaleId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.status.KunneIkkeOppdatereStatusPåKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.status.OppdaterStatusPåKontrollsamtaleCommand
import no.nav.su.se.bakover.presentation.web.toErrorJson
import java.util.UUID

fun Route.oppdaterStatusPåKontrollsamtale(
    kontrollsamtaleService: KontrollsamtaleService,
) {
    /**
     * @param journalpostId Gjelder for kontrollnotatet til veileder.
     */
    data class Body(
        val status: String?,
        val journalpostId: String?,
    ) {
        fun toKontrollsamtaleCommand(
            sakId: UUID,
            saksbehandler: NavIdentBruker.Saksbehandler,
            kontrollsamtaleId: UUID,
        ): Either<Resultat, OppdaterStatusPåKontrollsamtaleCommand> {
            val nyStatus = when (status) {
                "IKKE_MØTT_INNEN_FRIST" -> OppdaterStatusPåKontrollsamtaleCommand.OppdaterStatusTil.IkkeMøttInnenFrist
                "GJENNOMFØRT" -> OppdaterStatusPåKontrollsamtaleCommand.OppdaterStatusTil.Gjennomført(
                    journalpostId?.let {
                        JournalpostId(it)
                    } ?: return BadRequest.errorJson(
                        "JournalpostId må sendes inn dersom status er 'GJENNOMFØRT'",
                        "mangler_journalpostId",
                    ).left(),
                )

                else -> return BadRequest.errorJson(
                    "Ugyldig status. Forventer en av 'IKKE_MØTT_INNEN_FRIST', 'GJENNOMFØRT'",
                    "ugyldig_status",
                ).left()
            }

            return OppdaterStatusPåKontrollsamtaleCommand(
                sakId = sakId,
                kontrollsamtaleId = kontrollsamtaleId,
                saksbehandler = saksbehandler,
                nyStatus = nyStatus,
            ).right()
        }
    }

    patch("/saker/{sakId}/kontrollsamtaler/{kontrollsamtaleId}/status") {
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
                                kontrollsamtaleService.oppdaterStatusPåKontrollsamtale(it).fold(
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

private fun mapErrorToJsonResultat(
    error: KunneIkkeOppdatereStatusPåKontrollsamtale,
): Resultat {
    return when (error) {
        is KunneIkkeOppdatereStatusPåKontrollsamtale.UgyldigStatusovergang -> Feilresponser.ugyldigTilstand
        is KunneIkkeOppdatereStatusPåKontrollsamtale.FeilVedHentingAvJournalpost -> error.underliggendeFeil.toErrorJson()
        is KunneIkkeOppdatereStatusPåKontrollsamtale.JournalpostIkkeTilknyttetSak -> BadRequest.errorJson(
            "Journalposten er ikke tilknyttet saken",
            "journalpost_ikke_tilknyttet_sak",
        )
    }
}
