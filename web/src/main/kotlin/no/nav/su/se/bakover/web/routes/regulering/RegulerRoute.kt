package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.separateEither
import common.presentation.beregning.FradragRequestJson
import common.presentation.grunnlag.UføregrunnlagJson
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigBody
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigMåned
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.lesUUID
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withReguleringId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.KunneIkkeAvslutte
import no.nav.su.se.bakover.domain.regulering.KunneIkkeHenteReguleringsgrunnlag
import no.nav.su.se.bakover.domain.regulering.KunneIkkeRegulereManuelt
import no.nav.su.se.bakover.domain.regulering.KunneIkkeRegulereManuelt.Beregne
import no.nav.su.se.bakover.domain.regulering.ReguleringAutomatiskService
import no.nav.su.se.bakover.domain.regulering.ReguleringId
import no.nav.su.se.bakover.domain.regulering.ReguleringManuellService
import no.nav.su.se.bakover.domain.regulering.ReguleringStatusUteståendeService
import no.nav.su.se.bakover.web.routes.regulering.json.toJson
import vilkår.formue.domain.FormuegrenserFactory
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.uføre.domain.Uføregrad
import vilkår.uføre.domain.Uføregrunnlag
import java.time.Clock
import java.util.UUID

internal fun Route.reguler(
    reguleringManuellService: ReguleringManuellService,
    reguleringAutomatiskService: ReguleringAutomatiskService,
    reguleringStatusUteståendeService: ReguleringStatusUteståendeService,
    formuegrenserFactory: FormuegrenserFactory,
    clock: Clock,
    runtimeEnvironment: ApplicationConfig.RuntimeEnvironment,
) {
    route("$REGULERING_PATH/manuell/{reguleringId}") {
        get {
            authorize(Brukerrolle.Saksbehandler) {
                call.withReguleringId { id ->
                    reguleringManuellService.hentRegulering(
                        reguleringId = ReguleringId(id),
                        saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(formuegrenserFactory))))
                        },
                    )
                }
            }
        }
        post("beregn") {
            authorize(Brukerrolle.Saksbehandler) {
                call.withReguleringId { id ->
                    call.withBody<BeregnReguleringRequest> { body ->
                        sikkerLogg.debug("Verdier som ble sendt inn for manuell regulering: {}", body)
                        reguleringManuellService.beregnReguleringManuelt(
                            reguleringId = ReguleringId(id),
                            uføregrunnlag = body.uføre.toDomain(clock).getOrElse { return@authorize call.svar(it) },
                            fradrag = body.fradrag.toDomain(clock).getOrElse { return@authorize call.svar(it) },
                            saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                        ).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = {
                                call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                                call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(formuegrenserFactory))))
                            },

                        )
                    }
                }
            }
        }
        route("attestering") {
            post {
                authorize(Brukerrolle.Saksbehandler) {
                    call.withReguleringId { id ->
                        reguleringManuellService.reguleringTilAttestering(
                            reguleringId = ReguleringId(id),
                            saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                        ).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = {
                                call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                                call.svar(Resultat.okJson())
                            },
                        )
                    }
                }
            }
            post("godkjenn") {
                authorize(Brukerrolle.Attestant) {
                    call.withReguleringId { id ->
                        reguleringManuellService.godkjennRegulering(
                            reguleringId = ReguleringId(id),
                            attestant = NavIdentBruker.Attestant(call.suUserContext.navIdent),
                        ).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = {
                                call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                                call.svar(Resultat.okJson())
                            },
                        )
                    }
                }
            }
            post("underkjenn") {
                authorize(Brukerrolle.Attestant) {
                    call.withReguleringId { id ->
                        call.withBody<UnderkjennReguleringBody> { body ->
                            if (body.kommentar.isBlank()) {
                                call.svar(ugyldigBody)
                            }
                            reguleringManuellService.underkjennRegulering(
                                reguleringId = ReguleringId(id),
                                attestant = NavIdentBruker.Attestant(call.suUserContext.navIdent),
                                kommentar = body.kommentar,
                            ).fold(
                                ifLeft = { call.svar(it.tilResultat()) },
                                ifRight = {
                                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                                    call.svar(Resultat.okJson())
                                },
                            )
                        }
                    }
                }
            }
        }
        post("avslutt") {
            authorize(Brukerrolle.Saksbehandler) {
                call.lesUUID("reguleringId").fold(
                    ifLeft = {
                        call.svar(HttpStatusCode.BadRequest.errorJson(it, "reguleringId_mangler_eller_feil_format"))
                    },
                    ifRight = {
                        reguleringManuellService.avslutt(ReguleringId(it), call.suUserContext.saksbehandler).fold(
                            ifLeft = { feilmelding ->
                                call.svar(
                                    when (feilmelding) {
                                        KunneIkkeAvslutte.FantIkkeRegulering -> fantIkkeRegulering

                                        KunneIkkeAvslutte.UgyldigTilstand -> HttpStatusCode.BadRequest.errorJson(
                                            "Ugyldig tilstand på reguleringen",
                                            "regulering_ugyldig_tilstand",
                                        )
                                    },
                                )
                            },
                            ifRight = {
                                call.svar(Resultat.okJson())
                            },
                        )
                    },
                )
            }
        }
    }

    route("$REGULERING_PATH/automatisk") {
        post {
            authorize(Brukerrolle.Drift) {
                runBlocking {
                    call.withBody<AutomatiskReguleringBody> { body ->
                        val fraMåned =
                            Måned.parse(body.fraOgMedMåned) ?: return@runBlocking call.svar(ugyldigMåned)
                        if (runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Test) {
                            reguleringAutomatiskService.startAutomatiskRegulering(fraMåned)
                            call.svar(Resultat.okJson())
                        } else {
                            CoroutineScope(Dispatchers.IO).launch {
                                reguleringAutomatiskService.startAutomatiskRegulering(fraMåned)
                            }
                            call.svar(Resultat.accepted())
                        }
                    }
                }
            }
        }

        post("dry") {
            authorize(Brukerrolle.Drift) {
                runBlocking {
                    call.withBody<DryRunReguleringBody> { body ->
                        body.toCommand().fold(
                            ifLeft = { call.svar(it) },
                            ifRight = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    reguleringAutomatiskService.startAutomatiskReguleringForInnsyn(command = it)
                                }
                                call.svar(Resultat.accepted())
                            },
                        )
                    }
                }
            }
        }
    }

    get("$REGULERING_PATH/status-regulering-utestaende") {
        authorize(Brukerrolle.Drift) {
            val aar = call.parameters["aar"]?.toIntOrNull()
                ?: return@authorize call.svar(
                    HttpStatusCode.BadRequest.errorJson(
                        "aar parameter mangler eller er ugyldig",
                        "aar_mangler_eller_ugyldig",
                    ),
                )
            val status = reguleringStatusUteståendeService.hentStatusSisteGrunnbeløp(aar)
            call.svar(Resultat.json(HttpStatusCode.OK, serialize(status)))
        }
    }
}

data class BeregnReguleringRequest(val fradrag: List<FradragRequestJson>, val uføre: List<UføregrunnlagJson>)

data class UnderkjennReguleringBody(val kommentar: String)

private fun List<FradragRequestJson>.toDomain(clock: Clock): Either<Resultat, List<Fradragsgrunnlag>> {
    val (resultat, f) = this.map { it.toFradrag() }.separateEither()

    if (resultat.isNotEmpty()) return resultat.first().left()

    return f.map {
        Fradragsgrunnlag.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fradrag = it,
        ).getOrElse {
            return HttpStatusCode.BadRequest.errorJson(
                message = "Kunne ikke lage fradrag",
                code = "kunne_ikke_lage_fradrag",
            ).left()
        }
    }.right()
}

@JvmName("toDomainUføregrunnlagJson")
private fun List<UføregrunnlagJson>.toDomain(clock: Clock): Either<Resultat, List<Uføregrunnlag>> {
    return this.map {
        Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            periode = it.periode.toPeriode(),
            uføregrad = Uføregrad.tryParse(it.uføregrad).getOrElse {
                return Feilresponser.Uføre.uføregradMåVæreMellomEnOgHundre.left()
            },
            forventetInntekt = it.forventetInntekt,
        )
    }.right()
}

val fantIkkeRegulering = HttpStatusCode.BadRequest.errorJson(
    "Fant ikke regulering",
    "fant_ikke_regulering",
)

val reguleringErIkkeUnderBehandling = HttpStatusCode.BadRequest.errorJson(
    "Reguleringstype er ferdigstilt",
    "regulering_ikke_under_behandling",
)

val reguleringErAutomatisk = HttpStatusCode.BadRequest.errorJson(
    "Regulering er type automatisk",
    "regulering_er_automatisk",
)

val reguleringFeilBeregningsgrunnlag = HttpStatusCode.BadRequest.errorJson(
    "Feilet på grunn av beregningsgrunnlag",
    "regulering_feil_beregningsgrunnlag",
)

val reguleringFeiletUnderBeregening = HttpStatusCode.BadRequest.errorJson(
    "Regulering er type automatisk",
    "regulering_er_automatisk",
)

val reguleringFeilTilstandforAttestering = HttpStatusCode.BadRequest.errorJson(
    "Kan ikke sette regulering til attestering. Må være i tilstand beregnet",
    "regulering_feil_tilstand_attestering",
)
val reguleringFeilTilstandforIverksettelse = HttpStatusCode.BadRequest.errorJson(
    "Kan ikke iverksette regulering. Må være i tilstand til attestering",
    "regulering_feil_tilstand_iverksett",
)

val reguleringFeilTilstandforUnderkjennelse = HttpStatusCode.BadRequest.errorJson(
    "Kan ikke underkjenne regulering. Må være i tilstand til attestering",
    "regulering_feil_tilstand_underkjenn",
)

val reguleringSaksbehandlerKanIkkeAttestere = HttpStatusCode.BadRequest.errorJson(
    "Saksbehandler som har behandlet regulering kan ikke attestere",
    "regulering_saksbehandler_kan_ikke_attestere",
)

val fantIkkeVedtaksdata = HttpStatusCode.BadRequest.errorJson(
    "Fant ikke gjeldende vedtaksdata",
    "fant_ikke_vedtaksdata",
)

internal fun KunneIkkeHenteReguleringsgrunnlag.tilResultat(): Resultat = when (this) {
    KunneIkkeHenteReguleringsgrunnlag.FantIkkeRegulering -> fantIkkeRegulering
    KunneIkkeHenteReguleringsgrunnlag.FantIkkeGjeldendeVedtaksdata -> fantIkkeVedtaksdata
}

internal fun KunneIkkeRegulereManuelt.tilResultat() = when (this) {
    KunneIkkeRegulereManuelt.AlleredeFerdigstilt -> HttpStatusCode.BadRequest.errorJson(
        "Reguleringen er allerede ferdigstilt",
        "regulering_allerede_ferdigstilt",
    )

    KunneIkkeRegulereManuelt.ReguleringHarUtdatertePeriode -> HttpStatusCode.BadRequest.errorJson(
        "Periodene til regulering sine vilkårsvurderinger er utdatert.",
        "regulering_har_utdaterte_perioder",
    )

    KunneIkkeRegulereManuelt.FantIkkeRegulering -> fantIkkeRegulering
    KunneIkkeRegulereManuelt.BeregningOgSimuleringFeilet -> Feilresponser.ukjentBeregningOgSimuleringFeil
    KunneIkkeRegulereManuelt.StansetYtelseMåStartesFørDenKanReguleres -> HttpStatusCode.BadRequest.errorJson(
        "Stanset ytelse må startes før den kan reguleres",
        "stanset_ytelse_må_startes_før_den_kan_reguleres",
    )

    is Beregne.IkkeUnderBehandling -> reguleringErIkkeUnderBehandling
    is Beregne.ReguleringstypeAutomatisk -> reguleringErAutomatisk
    is Beregne.FeilMedBeregningsgrunnlag -> reguleringFeilBeregningsgrunnlag
    is Beregne -> reguleringFeiletUnderBeregening

    is KunneIkkeRegulereManuelt.KunneIkkeFerdigstille -> HttpStatusCode.InternalServerError.errorJson(
        "Kunne ikke ferdigstille regulering på grunn av ${this.feil}",
        "kunne_ikke_ferdigstille_regulering",
    )

    KunneIkkeRegulereManuelt.FantIkkeSak -> Feilresponser.fantIkkeSak
    KunneIkkeRegulereManuelt.AvventerKravgrunnlag -> Feilresponser.sakAvventerKravgrunnlagForTilbakekreving
    KunneIkkeRegulereManuelt.FeilTilstandForAttestering -> reguleringFeilTilstandforAttestering
    KunneIkkeRegulereManuelt.FeilTilstandForIverksettelse -> reguleringFeilTilstandforIverksettelse
    KunneIkkeRegulereManuelt.FeilTilstandForUnderkjennelse -> reguleringFeilTilstandforUnderkjennelse
    KunneIkkeRegulereManuelt.SaksbehandlerKanIkkeAttestere -> reguleringSaksbehandlerKanIkkeAttestere
}
