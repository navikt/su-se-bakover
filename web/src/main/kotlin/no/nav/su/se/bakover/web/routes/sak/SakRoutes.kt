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
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.sak.KunneIkkeHenteGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.parameter
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson
import no.nav.su.se.bakover.web.routes.grunnlag.toJson
import no.nav.su.se.bakover.web.routes.sak.BehandlingsoversiktJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId
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
                val saksnummer: String?,
            )
            call.withBody<Body> { body ->
                when {
                    body.fnr != null -> {
                        Either.catch { Fnr(body.fnr) }.fold(
                            ifLeft = {
                                call.svar(
                                    Feilresponser.ugyldigFødselsnummer,
                                )
                            },
                            ifRight = { fnr ->
                                sakService.hentSak(fnr)
                                    .mapLeft {
                                        call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                                        call.svar(
                                            NotFound.errorJson(
                                                "Fant ikke noen sak for person: ${body.fnr}",
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
                    sakService.hentBegrensetSakinfo(fnr)
                        .fold(
                            {
                                BegrensetSakinfoJson(
                                    harÅpenSøknad = false,
                                    iverksattInnvilgetStønadsperiode = null,
                                )
                            },
                            { info ->
                                BegrensetSakinfoJson(
                                    harÅpenSøknad = info.harÅpenSøknad,
                                    iverksattInnvilgetStønadsperiode = info.iverksattInnvilgetStønadsperiode?.toJson(),
                                )
                            },
                        )
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
