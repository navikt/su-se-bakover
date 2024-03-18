package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.separateEither
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.extensions.whenever
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
            data class Body(val fraOgMedMåned: String)
            call.withBody<Body> { body ->
                when (val m = Måned.parse(body.fraOgMedMåned)) {
                    null -> call.svar(ugyldigMåned)
                    else -> {
                        if (runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Test) {
                            reguleringService.startAutomatiskRegulering(m)
                            call.svar(Resultat.okJson())
                        } else {
                            CoroutineScope(Dispatchers.IO).launch {
                                reguleringService.startAutomatiskRegulering(m)
                            }
                            call.svar(Resultat.okJson())
                        }
                    }
                }
            }
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
                        // TODO -
                        supplement = Reguleringssupplement.empty(),
                    ).fold(
                        ifLeft = {
                            when (it) {
                                KunneIkkeRegulereManuelt.AlleredeFerdigstilt -> HttpStatusCode.BadRequest.errorJson(
                                    "Reguleringen er allerede ferdigstilt",
                                    "regulering_allerede_ferdigstilt",
                                )

                                KunneIkkeRegulereManuelt.FantIkkeRegulering -> fantIkkeRegulering

                                KunneIkkeRegulereManuelt.BeregningFeilet -> HttpStatusCode.InternalServerError.errorJson(
                                    "Beregning feilet",
                                    "beregning_feilet",
                                )

                                KunneIkkeRegulereManuelt.SimuleringFeilet -> HttpStatusCode.InternalServerError.errorJson(
                                    "Simulering feilet",
                                    "simulering_feilet",
                                )

                                KunneIkkeRegulereManuelt.StansetYtelseMåStartesFørDenKanReguleres -> HttpStatusCode.BadRequest.errorJson(
                                    "Stanset ytelse må startes før den kan reguleres",
                                    "stanset_ytelse_må_startes_før_den_kan_reguleres",
                                )

                                is KunneIkkeRegulereManuelt.KunneIkkeFerdigstille -> HttpStatusCode.InternalServerError.errorJson(
                                    "Kunne ikke ferdigstille regulering på grunn av ${it.feil}",
                                    "kunne_ikke_ferdigstille_regulering",
                                )

                                KunneIkkeRegulereManuelt.FantIkkeSak -> Feilresponser.fantIkkeSak
                                KunneIkkeRegulereManuelt.StansetYtelseMåStartesFørDenKanReguleres -> HttpStatusCode.BadRequest.errorJson(
                                    "Kan ikke regulere mens sak avventer kravgrunnlag",
                                    "Kan_ikke_regulere_mens_sak_avventer_kravgrunnlag",
                                )

                                KunneIkkeRegulereManuelt.AvventerKravgrunnlag -> HttpStatusCode.BadRequest.errorJson(
                                    "Avventer kravgrunnlag",
                                    "regulering_avventer_kravgrunnlag",
                                )
                            }.let { feilResultat ->
                                call.svar(feilResultat)
                            }
                        },
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
                    launch {
                        val parts = call.receiveMultipart()
                        var reguleringsdato: String = ""
                        var gVerdi: String = ""
                        var csvData: String = ""

                        parts.forEachPart {
                            when (it) {
                                is PartData.FileItem -> {
                                    val fileBytes = it.streamProvider().readBytes()
                                    csvData = String(fileBytes)
                                }

                                is PartData.FormItem -> {
                                    when (it.name) {
                                        "reguleringsdato" -> reguleringsdato = it.value
                                        "gVerdi" -> gVerdi = it.value
                                        else -> HttpStatusCode.BadRequest.errorJson(
                                            "Multipart inneholder ukjent formdata",
                                            "ukjent_formdata",
                                        )
                                    }
                                }

                                else -> HttpStatusCode.BadRequest.errorJson(
                                    "Multipart inneholder ukjent type. aksepterer kun filer og formdata",
                                    "ukjent_multipart_type",
                                )
                            }
                        }

                        val mappedCSV = csvData.split("\r\n").map {
                            val (fnr, fom, tom, type, beløp) = it.split(";")
                            SupplementInnholdAsCsv(fnr, fom, tom, type, beløp)
                        }

                        val gruppertSupplement = mappedCSV.groupBy { it.fnr }
                        val reguleringssupplementInnhold = gruppertSupplement.toReguleringssupplementInnhold()

                        val supplement = Reguleringssupplement(reguleringssupplementInnhold)

                        // TODO - call service
                        // TODO - handle left cases ved parsing av supplement
                        print(supplement)
                        print(reguleringsdato)
                        print(gVerdi)
                        call.svar(Resultat.okJson())
                    }
                },
                isFalse = {
                    launch {
                        /**
                         * @param fraOgMedMåned Måned i formatet yyyy-MM
                         * @param virkningstidspunkt Dato i formatet yyyy-MM-dd, hvis null settes den til fraOgMedMåned
                         * @param ikrafttredelse Dato i formatet yyyy-MM-dd, hvis null settes den til virkningstidspunkt
                         * @param grunnbeløp Hvis null, bruker vi bare eksisterende verdier
                         * @param garantipensjonOrdinær Hvis null, bruker vi bare eksisterende verdier
                         * @param garantipensjonHøy Hvis null, bruker vi bare eksisterende verdier
                         */
                        data class Body(
                            val fraOgMedMåned: String,
                            val csv: String,
                            val virkningstidspunkt: String?,
                            val ikrafttredelse: String?,
                            val grunnbeløp: Int? = null,
                            val garantipensjonOrdinær: Int? = null,
                            val garantipensjonHøy: Int? = null,
                        ) {
                            fun toCommand(): Either<Resultat, StartAutomatiskReguleringForInnsynCommand> {
                                val parsedFraOgMedMåned = Måned.parse(fraOgMedMåned) ?: return ugyldigMåned.left()
                                val parsedVirkningstidspunkt =
                                    virkningstidspunkt?.let {
                                        LocalDate.parse(it) ?: return Feilresponser.ugyldigDato.left()
                                    }
                                val parsedIkrafttredelse =
                                    ikrafttredelse?.let {
                                        LocalDate.parse(it) ?: return Feilresponser.ugyldigDato.left()
                                    }
                                return StartAutomatiskReguleringForInnsynCommand(
                                    fraOgMedMåned = parsedFraOgMedMåned,
                                    virkningstidspunkt = parsedVirkningstidspunkt ?: parsedFraOgMedMåned.fraOgMed,
                                    ikrafttredelse = parsedIkrafttredelse ?: parsedFraOgMedMåned.fraOgMed,
                                    grunnbeløp = grunnbeløp,
                                    garantipensjonOrdinær = garantipensjonOrdinær,
                                    garantipensjonHøy = garantipensjonHøy,
                                    supplement = Reguleringssupplement(
                                        csv.split("\n").map {
                                            val (fnr, fom, tom, type, beløp) = it.split(";")
                                            SupplementInnholdAsCsv(fnr, fom, tom, type, beløp)
                                        }.groupBy { it.fnr }.toReguleringssupplementInnhold(),
                                    ),
                                ).right()
                            }
                        }

                        call.withBody<Body> { body ->
                            call.svar(
                                body.toCommand().fold(
                                    { it },
                                    {
                                        CoroutineScope(Dispatchers.IO).launch {
                                            reguleringService.startAutomatiskReguleringForInnsyn(command = it)
                                        }
                                        Resultat.okJson()
                                    },
                                ),
                            )
                        }
                    }
                },
            )
        }
    }

    post("$REGULERING_PATH/supplement") {
        authorize(Brukerrolle.Drift) {
            // at den er multipart vil si at frontend har sendt inn supplementet som en fil
            val isMultipart = call.request.headers["content-type"]?.contains("multipart/form-data") ?: false

            isMultipart.whenever(
                isTrue = {
                    launch {
                        val part = call.receiveMultipart().readAllParts().single() as PartData.FileItem
                        val fileBytes = part.streamProvider().readBytes()
                        val csvData = String(fileBytes)

                        val mappedCSV = csvData.split("\r\n").map {
                            val (fnr, fom, tom, type, beløp) = it.split(";")
                            SupplementInnholdAsCsv(fnr, fom, tom, type, beløp)
                        }

                        val gruppertSupplement = mappedCSV.groupBy { it.fnr }
                        val reguleringssupplementInnhold = gruppertSupplement.toReguleringssupplementInnhold()

                        val supplement = Reguleringssupplement(reguleringssupplementInnhold)

                        // TODO - call service
                        println(supplement)
                        call.svar(Resultat.okJson())
                    }
                },
                isFalse = {
                    launch {
                        call.withBody<SupplementBody> {
                            val mappedCSV = it.csv.split("\n").map {
                                val (fnr, fom, tom, type, beløp) = it.split(";")
                                SupplementInnholdAsCsv(fnr, fom, tom, type, beløp)
                            }

                            val gruppertSupplement = mappedCSV.groupBy { it.fnr }
                            val reguleringssupplementInnhold = gruppertSupplement.toReguleringssupplementInnhold()

                            val supplement = Reguleringssupplement(reguleringssupplementInnhold)

                            // TODO - call service
                            println(supplement)
                            call.svar(Resultat.okJson())
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
