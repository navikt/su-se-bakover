package no.nav.su.se.bakover.web.routes.historisk

import arrow.core.Either
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FnrWrapper
import no.nav.su.se.bakover.service.historisk.SupstonadHistoriskService
import no.nav.su.se.bakover.web.routes.person.tilResultat
import person.domain.KunneIkkeHentePerson
import person.domain.PersonService

internal const val HISTORISK_ALDERSSAK_PATH = "/historisk/alderssak"

internal data class HarHistoriskAlderssakResponse(
    val harHistoriskAlderssak: Boolean,
)

internal data class HentHistoriskeAldersmånedsbeløpRequest(
    val vedtakId: Long,
)

internal fun Route.historiskAlderRoutes(
    supstonadHistoriskService: SupstonadHistoriskService,
    personService: PersonService,
    historiskAlderTestmodus: Boolean,
) {
    route(HISTORISK_ALDERSSAK_PATH) {
        post("/finnes") {
            authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
                call.withBody<FnrWrapper> { body ->
                    val fnr = body.fnr
                    sjekkTilgangTilHistoriskPerson(
                        fnr = fnr,
                        supstonadHistoriskService = supstonadHistoriskService,
                        personService = personService,
                        historiskAlderTestmodus = historiskAlderTestmodus,
                    ).fold(
                        ifLeft = {
                            call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                            call.svar(it.tilResultat())
                        },
                        ifRight = {
                            val harHistoriskAlderssak =
                                supstonadHistoriskService.harHistoriskAlderssak(fnr.value)
                            call.audit(
                                fnr,
                                if (harHistoriskAlderssak) {
                                    AuditLogEvent.Action.ACCESS
                                } else {
                                    AuditLogEvent.Action.SEARCH
                                },
                                null,
                            )
                            call.svar(
                                Resultat.json(
                                    HttpStatusCode.OK,
                                    serialize(HarHistoriskAlderssakResponse(harHistoriskAlderssak)),
                                ),
                            )
                        },
                    )
                }
            }
        }

        post("/vedtaksperioder") {
            authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
                call.withBody<FnrWrapper> { body ->
                    val fnr = body.fnr
                    sjekkTilgangTilHistoriskPerson(
                        fnr = fnr,
                        supstonadHistoriskService = supstonadHistoriskService,
                        personService = personService,
                        historiskAlderTestmodus = historiskAlderTestmodus,
                    ).fold(
                        ifLeft = {
                            call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                            call.svar(it.tilResultat())
                        },
                        ifRight = {
                            val vedtaksperioder = supstonadHistoriskService
                                .hentHistoriskeAldersvedtaksperioder(fnr.value)
                            call.audit(
                                fnr,
                                if (vedtaksperioder.isEmpty()) {
                                    AuditLogEvent.Action.SEARCH
                                } else {
                                    AuditLogEvent.Action.ACCESS
                                },
                                null,
                            )
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(vedtaksperioder)))
                        },
                    )
                }
            }
        }

        post("/manedsbelop") {
            authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
                call.withBody<HentHistoriskeAldersmånedsbeløpRequest> { body ->
                    val vedtakId = HistoriskVedtakId(body.vedtakId)
                    val oppslag = supstonadHistoriskService.hentHistoriskeAldersmånedsbeløp(vedtakId)
                        ?: return@withBody call.svar(
                            HttpStatusCode.NotFound.errorJson(
                                "Fant ikke historisk aldersvedtak",
                                "historisk_aldersvedtak_ikke_funnet",
                            ),
                        )

                    call.svar(
                        Resultat.json(
                            HttpStatusCode.OK,
                            serialize(oppslag.månedsbeløp),
                        ),
                    )

                    /* TODO: må sjekke dette via raskt oppslag via vedtak personident mot stonad tabell før man henter data
                    personService.sjekkTilgangTilPerson(oppslag.personident, Sakstype.ALDER).fold(
                        ifLeft = {
                            call.audit(oppslag.personident, AuditLogEvent.Action.SEARCH, null)
                            call.svar(it.tilResultat())
                        },
                        ifRight = {
                            call.audit(oppslag.personident, AuditLogEvent.Action.ACCESS, null)
                            call.svar(
                                Resultat.json(
                                    HttpStatusCode.OK,
                                    serialize(oppslag.månedsbeløp),
                                ),
                            )
                        },
                    )

                     */
                }
            }
        }
    }
}

internal fun sjekkTilgangTilHistoriskPerson(
    fnr: Fnr,
    supstonadHistoriskService: SupstonadHistoriskService,
    personService: PersonService,
    historiskAlderTestmodus: Boolean,
): Either<KunneIkkeHentePerson, Unit> = if (
    historiskAlderTestmodus &&
    supstonadHistoriskService.harHistoriskAlderssak(fnr.value)
) {
    Unit.right()
} else {
    personService.sjekkTilgangTilPerson(fnr, Sakstype.ALDER)
}
