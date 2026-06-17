package no.nav.su.se.bakover.web.routes.grunnlag.eksterneFradrag

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.regulering.MaksimumVedtakDto
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import person.domain.PersonService
import java.time.LocalDate
import java.util.UUID

internal fun Route.eksterneFradragRoutes(
    sakService: SakService,
    aapClient: AapApiInternClient,
    pesysClient: PesysClient,
    personService: PersonService,
) {
    route("/fradrag/eksternt/{sakId}") {
        hentFradragAlderspensjon(pesysClient, personService, sakService)
        hentFradragFraUføretrygd(pesysClient, personService, sakService)
        hentFradragFraArbeidsavklaringspenger(aapClient, personService, sakService)
    }
}

private fun harTilgang(personService: PersonService, sakService: SakService, sakId: UUID, fnr: Fnr): Boolean {
    val log: Logger = LoggerFactory.getLogger("harTilgangEksterneFradrag")

    val sak = sakService.hentSakInfo(sakId).getOrElse {
        log.info("Fant ikke sak med id: $sakId")
        return false
    }
    personService.sjekkTilgangTilPerson(sak.fnr, sak.type).getOrElse {
        log.warn("Ingen tilgang for sak med id: $sakId")
        return false
    }

    if (sak.fnr != fnr) {
        log.info("Tilgangssjekk mot eps på sakid: $sakId")
        // Her er det feks tidligere eps, trenger kun å sjekke tilgang
        personService.sjekkTilgangTilPerson(fnr, sak.type)
            .fold(
                ifLeft = {
                    log.error("Kunne ikke sjekke tilgang til eps sak.")
                    return false
                },
                ifRight = {
                    return true
                },
            )
    }
    return true
}

private fun Route.hentFradragAlderspensjon(
    pesysClient: PesysClient,
    personService: PersonService,
    sakService: SakService,
) {
    post("/alderspensjon") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withBody<HentFradragRequest> { request ->

                    if (harTilgang(personService, sakService, sakId, request.fnr)) {
                        pesysClient.hentVedtakForPersonPaaDatoAlder(listOf(request.fnr), request.periode.fraOgMed)
                            .fold(
                                ifLeft = {
                                    call.respond(HttpStatusCode.InternalServerError)
                                },
                                ifRight = { svar ->
                                    if (svar.feilendeFnr.isNotEmpty()) {
                                        call.respond(HttpStatusCode.InternalServerError)
                                    } else {
                                        call.audit(request.fnr, AuditLogEvent.Action.SEARCH, null)
                                        call.svar(Resultat.json(OK, serialize(svar.resultat)))
                                    }
                                },
                            )
                    } else {
                        call.respond(HttpStatusCode.Forbidden)
                    }
                }
            }
        }
    }
}

private fun Route.hentFradragFraUføretrygd(
    pesysClient: PesysClient,
    personService: PersonService,
    sakService: SakService,
) {
    post("/uforetrygd") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withBody<HentFradragRequest> { request ->
                    if (harTilgang(personService, sakService, sakId, request.fnr)) {
                        pesysClient.hentVedtakForPersonPaaDatoUføre(listOf(request.fnr), request.periode.fraOgMed)
                            .fold(
                                ifRight = { svar ->
                                    if (svar.feilendeFnr.isNotEmpty()) {
                                        call.respond(HttpStatusCode.InternalServerError)
                                    } else {
                                        call.audit(request.fnr, AuditLogEvent.Action.SEARCH, null)
                                        call.svar(Resultat.json(OK, serialize(svar.resultat)))
                                    }
                                },
                                ifLeft = {
                                    call.respond(HttpStatusCode.InternalServerError)
                                },
                            )
                    } else {
                        call.respond(HttpStatusCode.Forbidden)
                    }
                }
            }
        }
    }
}

private fun Route.hentFradragFraArbeidsavklaringspenger(
    aapClient: AapApiInternClient,
    personService: PersonService,
    sakService: SakService,
) {
    post("/arbeidsavklaringspenger") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withBody<HentFradragRequest> { request ->

                    if (harTilgang(personService, sakService, sakId, request.fnr)) {
                        aapClient.hentMaksimumUtenUtbetaling(
                            request.fnr,
                            request.periode.fraOgMed,
                            request.periode.tilOgMed,
                        ).fold(
                            ifLeft = {
                                call.respond(HttpStatusCode.InternalServerError)
                            },
                            ifRight = { fradrag ->
                                call.audit(request.fnr, AuditLogEvent.Action.SEARCH, null)
                                call.svar(Resultat.json(OK, serialize(fradrag.vedtak.map { it.mapToResponse() })))
                            },
                        )
                    } else {
                        call.respond(HttpStatusCode.Forbidden)
                    }
                }
            }
        }
    }
}

data class AapFradragResponse(
    val dagsats: Int,
    val barnetillegg: Int,
    val fraOgMedDato: LocalDate? = null,
    val tilOgMedDato: LocalDate? = null,
)

// TODO: Må vi ha med fiktiv utregning?
fun MaksimumVedtakDto.mapToResponse(): AapFradragResponse {
    return AapFradragResponse(
        dagsats = dagsats,
        barnetillegg = barnetillegg,
        fraOgMedDato = periode?.fraOgMedDato,
        tilOgMedDato = periode?.tilOgMedDato,
    )
}
