package no.nav.su.se.bakover.web.routes.sak

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.merge
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.parameter
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.sak.KunneIkkeHenteGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson
import no.nav.su.se.bakover.web.routes.grunnlag.toJson
import no.nav.su.se.bakover.web.routes.sak.BehandlingsoversiktJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import java.time.Clock
import java.time.LocalDate

internal const val sakPath = "/saker"

internal fun Route.sakRoutes(
    sakService: SakService,
    clock: Clock,
    satsFactory: SatsFactory,
) {
    post("$sakPath/søk") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            data class Body(
                val fnr: String?,
                val type: String?,
                val saksnummer: String?,
            )
            call.withBody<Body> { body ->
                when {
                    body.fnr != null -> {
                        Either.catch { Fnr(body.fnr) to Sakstype.from(body.type!!) }.fold(
                            ifLeft = {
                                if (Sakstype.values().none { it.value == body.type }) {
                                    call.svar(Feilresponser.ugyldigTypeSak)
                                } else {
                                    call.svar(
                                        Feilresponser.ugyldigFødselsnummer,
                                    )
                                }
                            },
                            ifRight = { (fnr, type) ->
                                sakService.hentSak(fnr, type)
                                    .mapLeft {
                                        call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                                        call.svar(
                                            NotFound.errorJson(
                                                "Fant ikke noen sak av typen ${body.type} for person: ${body.fnr}",
                                                "fant_ikke_sak_for_person",
                                            ),
                                        )
                                    }
                                    .map {
                                        call.audit(fnr, AuditLogEvent.Action.ACCESS, null)
                                        call.svar(
                                            Resultat.json(
                                                OK,
                                                serialize(it.toJson(clock, satsFactory)),
                                            ),
                                        )
                                    }
                            },
                        )
                    }
                    body.saksnummer != null -> {
                        Saksnummer.tryParse(body.saksnummer).fold(
                            ifLeft = {
                                call.svar(
                                    BadRequest.errorJson(
                                        "${body.saksnummer} er ikke et gyldig saksnummer",
                                        "saksnummer_ikke_gyldig",
                                    ),
                                )
                            },
                            ifRight = { saksnummer ->
                                call.svar(
                                    sakService.hentSak(saksnummer).fold(
                                        {
                                            NotFound.errorJson(
                                                "Fant ikke sak med saksnummer: ${body.saksnummer}",
                                                "fant_ikke_sak",
                                            )
                                        },
                                        {
                                            call.audit(it.fnr, AuditLogEvent.Action.ACCESS, null)
                                            Resultat.json(OK, serialize((it.toJson(clock, satsFactory))))
                                        },
                                    ),
                                )
                            },
                        )
                    }
                    else -> call.svar(
                        BadRequest.errorJson(
                            "Må oppgi enten saksnummer eller fødselsnummer",
                            "mangler_saksnummer_fødselsnummer",
                        ),
                    )
                }
            }
        }
    }

    get("$sakPath/info/{fnr}") {
        authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.parameter("fnr")
                .flatMap {
                    Either.catch { Fnr(it) }
                        .mapLeft { Feilresponser.ugyldigFødselsnummer }
                }
                .map { fnr ->
                    call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                    sakService.hentAlleredeGjeldendeSakForBruker(fnr)
                        .let { info ->
                            AlleredeGjeldendeSakForBrukerJson(
                                uføre = BegrensetSakinfoJson(
                                    harÅpenSøknad = info.uføre.harÅpenSøknad,
                                    iverksattInnvilgetStønadsperiode = info.uføre.iverksattInnvilgetStønadsperiode?.toJson(),
                                ),
                                alder = BegrensetSakinfoJson(
                                    harÅpenSøknad = info.alder.harÅpenSøknad,
                                    iverksattInnvilgetStønadsperiode = info.alder.iverksattInnvilgetStønadsperiode?.toJson(),
                                ),
                            )
                        }
                }.map {
                    Resultat.json(OK, objectMapper.writeValueAsString(it))
                }
                .merge()
                .let {
                    call.svar(it)
                }
        }
    }

    get("$sakPath/{sakId}") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.svar(
                    sakService.hentSak(sakId).fold(
                        { NotFound.errorJson("Fant ikke sak med id: $sakId", "fant_ikke_sak") },
                        {
                            call.audit(it.fnr, AuditLogEvent.Action.ACCESS, null)
                            Resultat.json(OK, serialize((it.toJson(clock, satsFactory))))
                        },
                    ),
                )
            }
        }
    }

    data class Body(val fraOgMed: LocalDate, val tilOgMed: LocalDate?) {
        val periode = Periode.create(fraOgMed, tilOgMed ?: LocalDate.MAX)
    }

    data class Response(val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson?)

    post("$sakPath/{sakId}/gjeldendeVedtaksdata") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBody<Body> { body ->
                    call.svar(
                        sakService.hentGjeldendeVedtaksdata(sakId, body.periode).fold(
                            {
                                when (it) {
                                    KunneIkkeHenteGjeldendeVedtaksdata.FantIkkeSak -> Feilresponser.fantIkkeSak
                                    KunneIkkeHenteGjeldendeVedtaksdata.IngenVedtak -> HttpStatusCode.NotFound.errorJson(
                                        message = "Fant ingen vedtak for ${body.periode}",
                                        code = "fant_ingen_vedtak_for_periode",
                                    )
                                }
                            },
                            {
                                Resultat.json(
                                    OK,
                                    serialize((Response(it?.grunnlagsdataOgVilkårsvurderinger?.toJson(satsFactory)))),
                                )
                            },
                        ),
                    )
                }
            }
        }
    }

    get("$sakPath/behandlinger/apne") {
        authorize(Brukerrolle.Saksbehandler) {
            val åpneBehandlinger = sakService.hentÅpneBehandlingerForAlleSaker()
            call.svar(Resultat.json(OK, serialize(åpneBehandlinger.toJson())))
        }
    }

    get("$sakPath/behandlinger/ferdige") {
        authorize(Brukerrolle.Saksbehandler) {
            val ferdigeBehandlinger = sakService.hentFerdigeBehandlingerForAlleSaker()
            call.svar(Resultat.json(OK, serialize(ferdigeBehandlinger.toJson())))
        }
    }
}
