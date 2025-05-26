package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.separateEither
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.whenever
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
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
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.DryRunNyttGrunnbeløp
import no.nav.su.se.bakover.domain.regulering.KunneIkkeAvslutte
import no.nav.su.se.bakover.domain.regulering.KunneIkkeRegulereManuelt
import no.nav.su.se.bakover.domain.regulering.ReguleringId
import no.nav.su.se.bakover.domain.regulering.ReguleringService
import no.nav.su.se.bakover.domain.regulering.StartAutomatiskReguleringForInnsynCommand
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import no.nav.su.se.bakover.web.routes.grunnlag.UføregrunnlagJson
import no.nav.su.se.bakover.web.routes.regulering.uttrekk.pesys.parseCSVFromString
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragRequestJson
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.uføre.domain.Uføregrad
import vilkår.uføre.domain.Uføregrunnlag
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// TODO - filnavnet er automatisk. vi har manuell route her inne også
internal fun Route.reguler(
    reguleringService: ReguleringService,
    clock: Clock,
    runtimeEnvironment: ApplicationConfig.RuntimeEnvironment,
) {
    post("$REGULERING_PATH/automatisk") {
        authorize(Brukerrolle.Drift) {
            val isMultipart = call.request.headers["content-type"]?.contains("multipart/form-data") ?: false

            isMultipart.whenever(
                isTrue = {
                    runBlocking {
                        val parts = call.receiveMultipart()
                        var fraOgMedMåned = ""
                        var csvData = ""

                        parts.forEachPart {
                            when (it) {
                                is PartData.FileItem -> {
                                    val fileBytes = it.provider().readRemaining().readByteArray()
                                    csvData = String(fileBytes)
                                }

                                is PartData.FormItem -> {
                                    when (it.name) {
                                        "fraOgMedMåned" -> fraOgMedMåned = it.value
                                        else -> Feilresponser.ukjentMultipartFormDataField
                                    }
                                }

                                else -> Feilresponser.ukjentMultipartType
                            }
                        }

                        parseCSVFromString(csvData, clock).fold(
                            ifLeft = { call.svar(it) },
                            ifRight = {
                                val fraMåned =
                                    Måned.parse(fraOgMedMåned) ?: return@runBlocking call.svar(ugyldigMåned)

                                if (runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Test) {
                                    reguleringService.startAutomatiskRegulering(fraMåned, it)
                                    call.svar(Resultat.okJson())
                                } else {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        reguleringService.startAutomatiskRegulering(fraMåned, it)
                                    }
                                    call.svar(Resultat.accepted())
                                }
                            },
                        )
                    }
                },
                isFalse = {
                    runBlocking {
                        call.withBody<AutomatiskReguleringBody> { body ->
                            val fraMåned =
                                Måned.parse(body.fraOgMedMåned) ?: return@runBlocking call.svar(ugyldigMåned)
                            val supplement = body.csv?.let {
                                parseCSVFromString(it, clock).fold(
                                    ifLeft = { return@runBlocking call.svar(it) },
                                    ifRight = { it },
                                )
                            } ?: Reguleringssupplement.empty(clock)

                            if (runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Test) {
                                reguleringService.startAutomatiskRegulering(fraMåned, supplement)
                                call.svar(Resultat.okJson())
                            } else {
                                CoroutineScope(Dispatchers.IO).launch {
                                    reguleringService.startAutomatiskRegulering(fraMåned, supplement)
                                }
                                call.svar(Resultat.accepted())
                            }
                        }
                    }
                },
            )
        }
    }

    post("$REGULERING_PATH/manuell/{reguleringId}") {
        authorize(Brukerrolle.Saksbehandler) {
            data class Body(val fradrag: List<FradragRequestJson>, val uføre: List<UføregrunnlagJson>)
            call.withReguleringId { id ->
                call.withBody<Body> { body ->
                    sikkerLogg.debug("Verdier som ble sendt inn for manuell regulering: {}", body)
                    reguleringService.regulerManuelt(
                        reguleringId = ReguleringId(id),
                        uføregrunnlag = body.uføre.toDomain(clock).getOrElse { return@authorize call.svar(it) },
                        fradrag = body.fradrag.toDomain(clock).getOrElse { return@authorize call.svar(it) },
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
    }

    post("$REGULERING_PATH/avslutt/{reguleringId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.lesUUID("reguleringId").fold(
                ifLeft = {
                    call.svar(HttpStatusCode.BadRequest.errorJson(it, "reguleringId_mangler_eller_feil_format"))
                },
                ifRight = {
                    reguleringService.avslutt(ReguleringId(it), call.suUserContext.saksbehandler).fold(
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

    post("$REGULERING_PATH/automatisk/dry") {
        authorize(Brukerrolle.Drift) {
            val isMultipart = call.request.headers["content-type"]?.contains("multipart/form-data") ?: false

            isMultipart.whenever(
                isTrue = {
                    runBlocking {
                        val parts = call.receiveMultipart()
                        var startDatoRegulering = ""
                        var gjeldendeSatsFra = ""
                        var file = ""

                        var virkningstidspunkt = ""
                        var ikrafttredelse = ""
                        var grunnbeløp = ""
                        var omregningsfaktor = ""

                        parts.forEachPart {
                            when (it) {
                                is PartData.FileItem -> file = String(it.provider().readRemaining().readByteArray())

                                is PartData.FormItem -> {
                                    when (it.name) {
                                        "virkningstidspunkt" -> virkningstidspunkt = it.value
                                        "ikrafttredelse" -> ikrafttredelse = it.value
                                        "startDatoRegulering" -> startDatoRegulering = it.value
                                        "gjeldendeSatsFra" -> gjeldendeSatsFra = it.value
                                        "grunnbeløp" -> grunnbeløp = it.value
                                        "omregningsfaktor" -> omregningsfaktor = it.value

                                        else -> Feilresponser.ukjentMultipartFormDataField
                                    }
                                }

                                else -> Feilresponser.ukjentMultipartType
                            }
                        }

                        val supplement = parseCSVFromString(file, clock).fold(
                            ifLeft = {
                                call.svar(it)
                                Reguleringssupplement.empty(clock)
                            },
                            ifRight = { it },
                        )

                        val dryRunNyttGrunnbeløp = if (listOf(
                                virkningstidspunkt,
                                ikrafttredelse,
                                grunnbeløp,
                                omregningsfaktor,
                            ).all { it.isEmpty() }
                        ) {
                            null
                        } else {
                            DryRunNyttGrunnbeløp(
                                virkningstidspunkt = LocalDate.parse(virkningstidspunkt),
                                ikrafttredelse = if (ikrafttredelse.isBlank()) {
                                    LocalDate.parse(virkningstidspunkt)
                                } else {
                                    LocalDate.parse(ikrafttredelse)
                                },
                                omregningsfaktor = BigDecimal(omregningsfaktor),
                                grunnbeløp = grunnbeløp.toInt(),
                            )
                        }

                        val command = StartAutomatiskReguleringForInnsynCommand(
                            startDatoRegulering = Måned.parse(startDatoRegulering) ?: return@runBlocking call.svar(
                                ugyldigMåned,
                            ),
                            gjeldendeSatsFra = LocalDate.parse(gjeldendeSatsFra),
                            dryRunNyttGrunnbeløp = dryRunNyttGrunnbeløp,
                            supplement = supplement,
                        )

                        launch {
                            reguleringService.startAutomatiskReguleringForInnsyn(command)
                        }
                        call.svar(Resultat.accepted())
                    }
                },
                isFalse = {
                    runBlocking {
                        call.withBody<DryRunReguleringBody> { body ->
                            body.toCommand(clock).fold(
                                ifLeft = { call.svar(it) },
                                ifRight = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        reguleringService.startAutomatiskReguleringForInnsyn(command = it)
                                    }
                                    call.svar(Resultat.accepted())
                                },
                            )
                        }
                    }
                },
            )
        }
    }

    /**
     * Denne routen er ment for å kunne ettersende reguleringssupplement
     */
    post("$REGULERING_PATH/supplement") {
        authorize(Brukerrolle.Drift) {
            val isMultipart = call.request.headers["content-type"]?.contains("multipart/form-data") ?: false

            isMultipart.whenever(
                isTrue = {
                    runBlocking {
                        val parts = call.receiveMultipart()

                        parts.forEachPart {
                            when (it) {
                                is PartData.FileItem -> {
                                    parseCSVFromString(
                                        String(it.provider().readRemaining().readByteArray()),
                                        clock,
                                    ).fold(
                                        ifLeft = { call.svar(it) },
                                        ifRight = {
                                            if (runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Test) {
                                                reguleringService.oppdaterReguleringerMedSupplement(it)
                                                call.svar(Resultat.okJson())
                                            } else {
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    reguleringService.oppdaterReguleringerMedSupplement(it)
                                                }
                                                call.svar(Resultat.accepted())
                                            }
                                        },
                                    )
                                }

                                else -> Feilresponser.ukjentMultipartType
                            }
                        }
                    }
                },
                isFalse = {
                    runBlocking {
                        call.withBody<EttersendingSupplementBody> { body ->
                            parseCSVFromString(body.csv, clock).fold(
                                ifLeft = { call.svar(it) },
                                ifRight = {
                                    if (runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Test) {
                                        reguleringService.oppdaterReguleringerMedSupplement(it)
                                        call.svar(Resultat.okJson())
                                    } else {
                                        CoroutineScope(Dispatchers.IO).launch {
                                            reguleringService.oppdaterReguleringerMedSupplement(it)
                                        }
                                        call.svar(Resultat.accepted())
                                    }
                                },
                            )
                        }
                    }
                },
            )
        }
    }
}

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

internal fun KunneIkkeRegulereManuelt.tilResultat(): Resultat = when (this) {
    KunneIkkeRegulereManuelt.AlleredeFerdigstilt -> HttpStatusCode.BadRequest.errorJson(
        "Reguleringen er allerede ferdigstilt",
        "regulering_allerede_ferdigstilt",
    )

    KunneIkkeRegulereManuelt.ReguleringHarUtdatertePeriode -> HttpStatusCode.BadRequest.errorJson(
        "Periodene til regulering sine vilkårsvurderinger er utdatert.",
        "regulering_har_utdaterte_perioder",
    )

    KunneIkkeRegulereManuelt.FantIkkeRegulering -> fantIkkeRegulering
    KunneIkkeRegulereManuelt.BeregningFeilet -> Feilresponser.ukjentBeregningFeil
    KunneIkkeRegulereManuelt.SimuleringFeilet -> Feilresponser.ukjentSimuleringFeil
    KunneIkkeRegulereManuelt.StansetYtelseMåStartesFørDenKanReguleres -> HttpStatusCode.BadRequest.errorJson(
        "Stanset ytelse må startes før den kan reguleres",
        "stanset_ytelse_må_startes_før_den_kan_reguleres",
    )

    is KunneIkkeRegulereManuelt.KunneIkkeFerdigstille -> HttpStatusCode.InternalServerError.errorJson(
        "Kunne ikke ferdigstille regulering på grunn av ${this.feil}",
        "kunne_ikke_ferdigstille_regulering",
    )

    KunneIkkeRegulereManuelt.FantIkkeSak -> Feilresponser.fantIkkeSak
    KunneIkkeRegulereManuelt.AvventerKravgrunnlag -> Feilresponser.sakAvventerKravgrunnlagForTilbakekreving
}
