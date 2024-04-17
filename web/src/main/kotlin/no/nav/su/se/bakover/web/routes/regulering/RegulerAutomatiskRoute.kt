package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.separateEither
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import no.nav.su.se.bakover.domain.regulering.KunneIkkeAvslutte
import no.nav.su.se.bakover.domain.regulering.KunneIkkeRegulereManuelt
import no.nav.su.se.bakover.domain.regulering.ReguleringId
import no.nav.su.se.bakover.domain.regulering.ReguleringService
import no.nav.su.se.bakover.domain.regulering.Reguleringssupplement
import no.nav.su.se.bakover.domain.regulering.StartAutomatiskReguleringForInnsynCommand
import no.nav.su.se.bakover.web.routes.grunnlag.UføregrunnlagJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragRequestJson
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.uføre.domain.Uføregrad
import vilkår.uføre.domain.Uføregrunnlag
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
                                    val fileBytes = it.streamProvider().readBytes()
                                    csvData = String(fileBytes)
                                }

                                is PartData.FormItem -> {
                                    when (it.name) {
                                        "fraOgMedMåned" -> fraOgMedMåned = it.value
                                        else -> Feilresponser.ukjentFormData
                                    }
                                }

                                else -> Feilresponser.ukjentMultipartType
                            }
                        }

                        parseCSVFromString(csvData).fold(
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
                                parseCSVFromString(it).fold(
                                    ifLeft = { return@runBlocking call.svar(it) },
                                    ifRight = { it },
                                )
                            } ?: Reguleringssupplement.empty()

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
                        var fraOgMedMåned = ""
                        var gVerdi = ""
                        var csvData = ""

                        parts.forEachPart {
                            when (it) {
                                is PartData.FileItem -> {
                                    val fileBytes = it.streamProvider().readBytes()
                                    csvData = String(fileBytes)
                                }

                                is PartData.FormItem -> {
                                    when (it.name) {
                                        "fraOgMedMåned" -> fraOgMedMåned = it.value
                                        "gVerdi" -> gVerdi = it.value
                                        else -> Feilresponser.ukjentFormData
                                    }
                                }

                                else -> Feilresponser.ukjentMultipartType
                            }
                        }

                        parseCSVFromString(csvData).fold(
                            ifLeft = { call.svar(it) },
                            ifRight = {
                                val command = StartAutomatiskReguleringForInnsynCommand(
                                    fraOgMedMåned = Måned.fra(LocalDate.parse(fraOgMedMåned)),
                                    virkningstidspunkt = LocalDate.parse(fraOgMedMåned),
                                    supplement = it,
                                    grunnbeløp = gVerdi.toIntOrNull() ?: return@runBlocking call.svar(
                                        HttpStatusCode.BadRequest.errorJson(
                                            "Kunne ikke parse grunnbeløp",
                                            "feil_ved_parsning_av_grunnbeløp",
                                        ),
                                    ),
                                )

                                launch {
                                    reguleringService.startAutomatiskReguleringForInnsyn(command)
                                }
                                call.svar(Resultat.accepted())
                            },
                        )
                    }
                },
                isFalse = {
                    runBlocking {
                        call.withBody<DryRunReguleringBody> { body ->
                            body.toCommand().fold(
                                ifLeft = { call.svar(it) },
                                ifRight = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        reguleringService.startAutomatiskReguleringForInnsyn(command = it)
                                    }
                                    Resultat.accepted()
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
                        var fraOgMedMåned = ""
                        var csvData = ""

                        parts.forEachPart {
                            when (it) {
                                is PartData.FileItem -> {
                                    val fileBytes = it.streamProvider().readBytes()
                                    csvData = String(fileBytes)
                                }

                                is PartData.FormItem -> {
                                    when (it.name) {
                                        "fraOgMedMåned" -> fraOgMedMåned = it.value
                                        else -> Feilresponser.ukjentFormData
                                    }
                                }

                                else -> Feilresponser.ukjentMultipartType
                            }
                        }

                        parseCSVFromString(csvData).fold(
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
                        call.withBody<SupplementBody> { body ->
                            parseCSVFromString(body.csv).fold(
                                ifLeft = { call.svar(it) },
                                ifRight = {
                                    val fraMåned =
                                        Måned.parse(body.fraOgMedMåned) ?: return@runBlocking call.svar(ugyldigMåned)

                                    if (runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Test) {
                                        reguleringService.oppdaterReguleringerMedSupplement(fraMåned, it)
                                        call.svar(Resultat.okJson())
                                    } else {
                                        CoroutineScope(Dispatchers.IO).launch {
                                            reguleringService.oppdaterReguleringerMedSupplement(fraMåned, it)
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
