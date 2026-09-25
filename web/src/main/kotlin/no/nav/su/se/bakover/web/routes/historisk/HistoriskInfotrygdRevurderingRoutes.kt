package no.nav.su.se.bakover.web.routes.historisk

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.revurdering.domain.Opphørsgrunn
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdForhåndsvarsel
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdManueltOpphør
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdVedtaksbrevvalg
import no.nav.su.se.bakover.domain.historisk.revurdering.KunneIkkeOppretteHistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.brev.KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando
import no.nav.su.se.bakover.service.historisk.SupstonadHistoriskService
import no.nav.su.se.bakover.service.historisk.revurdering.BeregnHistoriskInfotrygdRevurderingCommand
import no.nav.su.se.bakover.service.historisk.revurdering.HistoriskInfotrygdBeregningResultat
import no.nav.su.se.bakover.service.historisk.revurdering.HistoriskInfotrygdRevurderingService
import no.nav.su.se.bakover.service.historisk.revurdering.HistoriskInfotrygdRevurderingService.HistoriskInfotrygdMånedsgrunnlag
import no.nav.su.se.bakover.service.historisk.revurdering.HistoriskInfotrygdRevurderingService.HistoriskInfotrygdMånedsgrunnlagForMåned
import no.nav.su.se.bakover.service.historisk.revurdering.KunneIkkeBeregneHistoriskInfotrygdRevurderingService
import no.nav.su.se.bakover.service.historisk.revurdering.KunneIkkeEndreHistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.service.historisk.revurdering.KunneIkkeLageHistoriskInfotrygdForhåndsvarsel
import no.nav.su.se.bakover.service.historisk.revurdering.KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast
import no.nav.su.se.bakover.service.historisk.revurdering.KunneIkkeOppretteHistoriskInfotrygdRevurderingService
import no.nav.su.se.bakover.service.historisk.revurdering.KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel
import no.nav.su.se.bakover.service.historisk.revurdering.OpprettHistoriskInfotrygdRevurderingCommand
import no.nav.su.se.bakover.web.inputvalidation.InputValidator
import no.nav.su.se.bakover.web.routes.person.tilResultat
import person.domain.PersonService
import satser.domain.historisk.HistoriskInfotrygdSatskategori
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.util.UUID

internal data class OpprettHistoriskInfotrygdRevurderingRequest(
    val fnr: Fnr,
    val periode: PeriodeJson,
)

internal data class HistoriskInfotrygdRevurderingOversiktRequest(
    val fnr: Fnr,
)

internal data class HistoriskInfotrygdRevurderingResponse(
    val id: UUID,
    val sakId: UUID,
    val periode: PeriodeJson,
    val status: String,
    val begrunnelse: String?,
    val vedtaksbrevvalg: String,
    val vedtaksbrevFritekst: String?,
    val kreverKontrollAvHistoriskForsørgingstillegg: Boolean,
    val harBekreftetKontrollAvHistoriskForsørgingstillegg: Boolean,
    val forhåndsvarsel: HistoriskInfotrygdForhåndsvarselResponse,
    val sperregrunnerForAttestering: List<String>,
    val opprettet: String,
    val oppdatert: String,
)

internal data class BegrunnelseRequest(
    val begrunnelse: String,
)

internal data class HistoriskInfotrygdForhåndsvarselRequest(
    val fritekst: String,
)

internal data class HistoriskInfotrygdIkkeSendForhåndsvarselRequest(
    val begrunnelse: String,
)

internal data class HistoriskInfotrygdForhåndsvarselResponse(
    val status: String,
    val fritekst: String?,
    val begrunnelse: String?,
    val tidspunkt: String?,
    val erUtdatert: Boolean,
)

internal data class BeregnHistoriskInfotrygdRevurderingRequest(
    val begrunnelse: String,
    val måneder: List<HistoriskInfotrygdBeregningsgrunnlagForMånedRequest>,
)

internal data class HistoriskInfotrygdBeregningsgrunnlagForMånedRequest(
    val måned: String,
    val satskategori: HistoriskInfotrygdSatskategori,
    val fradrag: List<HistoriskInfotrygdFradragForMånedRequest>,
    val manueltOpphør: HistoriskInfotrygdManueltOpphørRequest?,
    val gjeninnvilgelsesbegrunnelse: String?,
)

internal data class HistoriskInfotrygdManueltOpphørRequest(
    val opphørsgrunn: Opphørsgrunn,
    val begrunnelse: String,
)

internal data class HistoriskInfotrygdFradragForMånedRequest(
    val type: String,
    val beskrivelse: String?,
    val månedsbeløp: Double,
    val utenlandskInntekt: HistoriskInfotrygdUtenlandskInntektRequest?,
    val tilhører: FradragTilhører,
)

internal data class HistoriskInfotrygdUtenlandskInntektRequest(
    val beløpIUtenlandskValuta: Int,
    val valuta: String,
    val kurs: Double,
)

internal data class HistoriskInfotrygdBeregningResponse(
    val behandling: HistoriskInfotrygdRevurderingResponse,
    val økonomiskRetning: String,
    val måneder: List<HistoriskInfotrygdBeregningForMånedResponse>,
)

internal data class HistoriskInfotrygdBeregningForMånedResponse(
    val måned: String,
    val satskategori: HistoriskInfotrygdSatskategori,
    val fradrag: List<HistoriskInfotrygdFradragForMånedResponse>,
    val manueltOpphør: HistoriskInfotrygdManueltOpphørResponse?,
    val gammeltBeløp: BigDecimal,
    val nyttBeløp: BigDecimal,
    val differanse: BigDecimal,
    val nyttResultat: String,
    val opphørsgrunn: String?,
    val begrunnelse: String?,
    val gjeninnvilgelsesbegrunnelse: String?,
)

internal data class HistoriskInfotrygdManueltOpphørResponse(
    val opphørsgrunn: Opphørsgrunn,
    val begrunnelse: String,
)

internal data class HistoriskInfotrygdFradragForMånedResponse(
    val type: String,
    val beskrivelse: String?,
    val månedsbeløp: Double,
    val utenlandskInntekt: HistoriskInfotrygdUtenlandskInntektRequest?,
    val tilhører: FradragTilhører,
)

internal data class HistoriskInfotrygdMånedsgrunnlagResponse(
    val revurderingId: UUID,
    val kreverKontrollAvHistoriskForsørgingstillegg: Boolean,
    val harBekreftetKontrollAvHistoriskForsørgingstillegg: Boolean,
    val måneder: List<HistoriskInfotrygdMånedsgrunnlagForMånedResponse>,
    val beregning: HistoriskInfotrygdLagretBeregningResponse?,
)

internal data class HistoriskInfotrygdLagretBeregningResponse(
    val begrunnelse: String,
    val økonomiskRetning: String,
    val måneder: List<HistoriskInfotrygdBeregningForMånedResponse>,
)

internal data class HistoriskInfotrygdMånedsgrunnlagForMånedResponse(
    val måned: String,
    val resultat: String,
    val stønadsstart: LocalDate?,
    val opprinneligStønadId: Long?,
    val opprinneligVedtakId: Long?,
    val oppdragId: String?,
    val kilde: String,
    val historiskSats: BigDecimal?,
    val historiskFradrag: BigDecimal?,
    val historiskFradragskoder: List<String>,
    val historiskBeløp: BigDecimal?,
    val foreslåttSatskategori: String?,
    val kreverKontrollAvHistoriskForsørgingstillegg: Boolean,
)

internal data class OppdaterHistoriskInfotrygdVedtaksbrevRequest(
    val valg: HistoriskInfotrygdVedtaksbrevvalgRequest,
    val fritekst: String?,
)

internal enum class HistoriskInfotrygdVedtaksbrevvalgRequest {
    SEND,
    IKKE_SEND,
}

internal data class OverlappendeHistoriskInfotrygdRevurderingResponse(
    val message: String,
    val code: String,
    val eksisterendeRevurderingId: UUID,
    val sakId: UUID,
)

internal fun Route.historiskInfotrygdRevurderingRoutes(
    service: HistoriskInfotrygdRevurderingService,
    supstonadHistoriskService: SupstonadHistoriskService,
    personService: PersonService,
    historiskAlderTestmodus: Boolean,
) {
    route("$HISTORISK_ALDERSSAK_PATH/revurderinger") {
        post("/oversikt") {
            authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
                call.withBody<HistoriskInfotrygdRevurderingOversiktRequest> { body ->
                    sjekkTilgangTilHistoriskPerson(
                        fnr = body.fnr,
                        supstonadHistoriskService = supstonadHistoriskService,
                        personService = personService,
                        historiskAlderTestmodus = historiskAlderTestmodus,
                    ).fold(
                        ifLeft = {
                            call.audit(body.fnr, AuditLogEvent.Action.SEARCH, null)
                            call.svar(it.tilResultat())
                        },
                        ifRight = {
                            val behandlinger = service.hentForPerson(body.fnr)
                            call.audit(body.fnr, AuditLogEvent.Action.ACCESS, null)
                            call.svar(
                                Resultat.json(
                                    HttpStatusCode.OK,
                                    serialize(behandlinger.map { it.toResponse() }),
                                ),
                            )
                        },
                    )
                }
            }
        }

        post {
            authorize(Brukerrolle.Saksbehandler) {
                call.withBody<OpprettHistoriskInfotrygdRevurderingRequest> { body ->
                    sjekkTilgangTilHistoriskPerson(
                        fnr = body.fnr,
                        supstonadHistoriskService = supstonadHistoriskService,
                        personService = personService,
                        historiskAlderTestmodus = historiskAlderTestmodus,
                    ).fold(
                        ifLeft = {
                            call.audit(body.fnr, AuditLogEvent.Action.SEARCH, null)
                            call.svar(it.tilResultat())
                        },
                        ifRight = {
                            service.opprett(
                                OpprettHistoriskInfotrygdRevurderingCommand(
                                    fnr = body.fnr,
                                    periode = body.periode.toPeriode(),
                                    saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                                ),
                            ).fold(
                                ifLeft = { feil ->
                                    call.audit(body.fnr, AuditLogEvent.Action.ACCESS, null)
                                    call.svar(feil.tilResultat())
                                },
                                ifRight = { revurdering ->
                                    call.audit(
                                        body.fnr,
                                        AuditLogEvent.Action.UPDATE,
                                        revurdering.id.value,
                                    )
                                    call.svar(
                                        Resultat.json(
                                            HttpStatusCode.Created,
                                            serialize(revurdering.toResponse()),
                                        ),
                                    )
                                },
                            )
                        },
                    )
                }
            }
        }

        get("/{revurderingId}") {
            authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
                val id = call.parameters["revurderingId"].tilRevurderingId()
                    ?: return@authorize call.svar(ugyldigRevurderingId())
                val (sakInfo, revurdering) = service.hentMedSakInfo(id)
                    ?: return@authorize call.svar(fantIkkeRevurdering())

                personService.sjekkTilgangTilPerson(sakInfo.fnr, sakInfo.type).fold(
                    ifLeft = {
                        call.audit(sakInfo.fnr, AuditLogEvent.Action.SEARCH, revurdering.id.value)
                        call.svar(it.tilResultat())
                    },
                    ifRight = {
                        call.audit(sakInfo.fnr, AuditLogEvent.Action.ACCESS, revurdering.id.value)
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(revurdering.toResponse())))
                    },
                )
            }
        }

        post("/{revurderingId}/vedtaksbrev") {
            authorize(Brukerrolle.Saksbehandler) {
                val id = call.parameters["revurderingId"].tilRevurderingId()
                    ?: return@authorize call.svar(ugyldigRevurderingId())
                val (sakInfo, eksisterende) = service.hentMedSakInfo(id)
                    ?: return@authorize call.svar(fantIkkeRevurdering())

                call.withBody<OppdaterHistoriskInfotrygdVedtaksbrevRequest> { body ->
                    InputValidator.validerFritekst(body.fritekst)?.let {
                        return@withBody call.svar(
                            HttpStatusCode.BadRequest.errorJson(
                                message = "Friteksten inneholder ugyldige tegn eller er for lang",
                                code = "ugyldig_historisk_infotrygd_vedtaksbrev_fritekst",
                            ),
                        )
                    }
                    personService.sjekkTilgangTilPerson(sakInfo.fnr, sakInfo.type).fold(
                        ifLeft = {
                            call.audit(sakInfo.fnr, AuditLogEvent.Action.SEARCH, eksisterende.id.value)
                            call.svar(it.tilResultat())
                        },
                        ifRight = {
                            val valg = when (body.valg) {
                                HistoriskInfotrygdVedtaksbrevvalgRequest.SEND ->
                                    HistoriskInfotrygdVedtaksbrevvalg.SEND
                                HistoriskInfotrygdVedtaksbrevvalgRequest.IKKE_SEND ->
                                    HistoriskInfotrygdVedtaksbrevvalg.IKKE_SEND
                            }
                            service.oppdaterVedtaksbrev(
                                id = id,
                                valg = valg,
                                fritekst = body.fritekst,
                                saksbehandler = call.suUserContext.saksbehandler,
                            ).fold(
                                ifLeft = { call.svar(it.tilResultat()) },
                                ifRight = { oppdatert ->
                                    call.audit(sakInfo.fnr, AuditLogEvent.Action.UPDATE, oppdatert.id.value)
                                    call.svar(
                                        Resultat.json(HttpStatusCode.OK, serialize(oppdatert.toResponse())),
                                    )
                                },
                            )
                        },
                    )
                }
            }
        }

        get("/{revurderingId}/vedtaksbrevutkast") {
            authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
                val id = call.parameters["revurderingId"].tilRevurderingId()
                    ?: return@authorize call.svar(ugyldigRevurderingId())
                val (sakInfo, revurdering) = service.hentMedSakInfo(id)
                    ?: return@authorize call.svar(fantIkkeRevurdering())

                personService.sjekkTilgangTilPerson(sakInfo.fnr, sakInfo.type).fold(
                    ifLeft = {
                        call.audit(sakInfo.fnr, AuditLogEvent.Action.SEARCH, revurdering.id.value)
                        call.svar(it.tilResultat())
                    },
                    ifRight = {
                        service.lagVedtaksbrevutkast(id).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = {
                                call.sikkerlogg(
                                    "Laget vedtaksbrevutkast for historisk Infotrygd-revurdering ${id.value}",
                                )
                                call.audit(sakInfo.fnr, AuditLogEvent.Action.ACCESS, revurdering.id.value)
                                call.respondBytes(it.getContent(), ContentType.Application.Pdf)
                            },
                        )
                    },
                )
            }
        }
        post("/{revurderingId}/send-til-attestering") {
            authorize(Brukerrolle.Saksbehandler) {
                val id = call.parameters["revurderingId"].tilRevurderingId()
                    ?: return@authorize call.svar(ugyldigRevurderingId())
                val (sakInfo, eksisterende) = service.hentMedSakInfo(id)
                    ?: return@authorize call.svar(fantIkkeRevurdering())

                personService.sjekkTilgangTilPerson(sakInfo.fnr, sakInfo.type).fold(
                    ifLeft = {
                        call.audit(sakInfo.fnr, AuditLogEvent.Action.SEARCH, eksisterende.id.value)
                        call.svar(it.tilResultat())
                    },
                    ifRight = {
                        service.sendTilAttestering(id, call.suUserContext.saksbehandler).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = { oppdatert ->
                                call.audit(sakInfo.fnr, AuditLogEvent.Action.UPDATE, oppdatert.id.value)
                                call.svar(Resultat.json(HttpStatusCode.OK, serialize(oppdatert.toResponse())))
                            },
                        )
                    },
                )
            }
        }

        get("/{revurderingId}/maanedsgrunnlag") {
            authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
                val id = call.parameters["revurderingId"].tilRevurderingId()
                    ?: return@authorize call.svar(ugyldigRevurderingId())
                val (sakInfo, revurdering) = service.hentMedSakInfo(id)
                    ?: return@authorize call.svar(fantIkkeRevurdering())

                personService.sjekkTilgangTilPerson(sakInfo.fnr, sakInfo.type).fold(
                    ifLeft = {
                        call.audit(sakInfo.fnr, AuditLogEvent.Action.SEARCH, revurdering.id.value)
                        call.svar(it.tilResultat())
                    },
                    ifRight = {
                        val grunnlag = service.hentMånedsgrunnlag(id)
                        if (grunnlag == null) {
                            call.svar(fantIkkeRevurdering())
                        } else {
                            call.audit(sakInfo.fnr, AuditLogEvent.Action.ACCESS, revurdering.id.value)
                            call.svar(
                                Resultat.json(HttpStatusCode.OK, serialize(grunnlag.toResponse())),
                            )
                        }
                    },
                )
            }
        }

        post("/{revurderingId}/forsorgingstillegg/bekreft") {
            authorize(Brukerrolle.Saksbehandler) {
                val id = call.parameters["revurderingId"].tilRevurderingId()
                    ?: return@authorize call.svar(ugyldigRevurderingId())
                val (sakInfo, eksisterende) = service.hentMedSakInfo(id)
                    ?: return@authorize call.svar(fantIkkeRevurdering())

                personService.sjekkTilgangTilPerson(sakInfo.fnr, sakInfo.type).fold(
                    ifLeft = {
                        call.audit(sakInfo.fnr, AuditLogEvent.Action.SEARCH, eksisterende.id.value)
                        call.svar(it.tilResultat())
                    },
                    ifRight = {
                        service.bekreftKontrollAvHistoriskForsørgingstillegg(
                            id = id,
                            saksbehandler = call.suUserContext.saksbehandler,
                        ).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = { oppdatert ->
                                call.audit(sakInfo.fnr, AuditLogEvent.Action.UPDATE, oppdatert.id.value)
                                call.svar(
                                    Resultat.json(HttpStatusCode.OK, serialize(oppdatert.toResponse())),
                                )
                            },
                        )
                    },
                )
            }
        }

        post("/{revurderingId}/beregning") {
            authorize(Brukerrolle.Saksbehandler) {
                val id = call.parameters["revurderingId"].tilRevurderingId()
                    ?: return@authorize call.svar(ugyldigRevurderingId())
                val (sakInfo, eksisterende) = service.hentMedSakInfo(id)
                    ?: return@authorize call.svar(fantIkkeRevurdering())

                call.withBody<BeregnHistoriskInfotrygdRevurderingRequest> { body ->
                    val command = body.toCommand().fold(
                        ifLeft = {
                            return@withBody call.svar(
                                HttpStatusCode.BadRequest.errorJson(
                                    message = it,
                                    code = "ugyldig_historisk_infotrygd_beregningsgrunnlag",
                                ),
                            )
                        },
                        ifRight = { it },
                    )
                    personService.sjekkTilgangTilPerson(sakInfo.fnr, sakInfo.type).fold(
                        ifLeft = {
                            call.audit(sakInfo.fnr, AuditLogEvent.Action.SEARCH, eksisterende.id.value)
                            call.svar(it.tilResultat())
                        },
                        ifRight = {
                            service.beregn(
                                id = id,
                                command = command,
                                saksbehandler = call.suUserContext.saksbehandler,
                            ).fold(
                                ifLeft = { call.svar(it.tilResultat()) },
                                ifRight = { resultat ->
                                    val oppdatert = requireNotNull(resultat.revurdering)
                                    call.audit(
                                        sakInfo.fnr,
                                        AuditLogEvent.Action.UPDATE,
                                        oppdatert.id.value,
                                    )
                                    call.svar(
                                        Resultat.json(HttpStatusCode.OK, serialize(resultat.toResponse())),
                                    )
                                },
                            )
                        },
                    )
                }
            }
        }

        post("/{revurderingId}/forhandsvarsel/utkast") {
            authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
                val id = call.parameters["revurderingId"].tilRevurderingId()
                    ?: return@authorize call.svar(ugyldigRevurderingId())
                val (sakInfo, revurdering) = service.hentMedSakInfo(id)
                    ?: return@authorize call.svar(fantIkkeRevurdering())

                call.withBody<HistoriskInfotrygdForhåndsvarselRequest> { body ->
                    personService.sjekkTilgangTilPerson(sakInfo.fnr, sakInfo.type).fold(
                        ifLeft = {
                            call.audit(sakInfo.fnr, AuditLogEvent.Action.SEARCH, revurdering.id.value)
                            call.svar(it.tilResultat())
                        },
                        ifRight = {
                            service.lagForhåndsvarselutkast(
                                id = id,
                                fritekst = body.fritekst,
                                saksbehandler = revurdering.saksbehandler,
                            ).fold(
                                ifLeft = { call.svar(it.tilResultat()) },
                                ifRight = {
                                    call.audit(sakInfo.fnr, AuditLogEvent.Action.ACCESS, revurdering.id.value)
                                    call.respondBytes(it.getContent(), ContentType.Application.Pdf)
                                },
                            )
                        },
                    )
                }
            }
        }

        post("/{revurderingId}/forhandsvarsel/send") {
            authorize(Brukerrolle.Saksbehandler) {
                val id = call.parameters["revurderingId"].tilRevurderingId()
                    ?: return@authorize call.svar(ugyldigRevurderingId())
                val (sakInfo, revurdering) = service.hentMedSakInfo(id)
                    ?: return@authorize call.svar(fantIkkeRevurdering())

                call.withBody<HistoriskInfotrygdForhåndsvarselRequest> { body ->
                    personService.sjekkTilgangTilPerson(sakInfo.fnr, sakInfo.type).fold(
                        ifLeft = {
                            call.audit(sakInfo.fnr, AuditLogEvent.Action.SEARCH, revurdering.id.value)
                            call.svar(it.tilResultat())
                        },
                        ifRight = {
                            service.sendForhåndsvarsel(
                                id = id,
                                fritekst = body.fritekst,
                                saksbehandler = call.suUserContext.saksbehandler,
                            ).fold(
                                ifLeft = { call.svar(it.tilResultat()) },
                                ifRight = {
                                    call.audit(sakInfo.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                                    call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toResponse())))
                                },
                            )
                        },
                    )
                }
            }
        }

        post("/{revurderingId}/forhandsvarsel/ikke-send") {
            authorize(Brukerrolle.Saksbehandler) {
                val id = call.parameters["revurderingId"].tilRevurderingId()
                    ?: return@authorize call.svar(ugyldigRevurderingId())
                val (sakInfo, revurdering) = service.hentMedSakInfo(id)
                    ?: return@authorize call.svar(fantIkkeRevurdering())

                call.withBody<HistoriskInfotrygdIkkeSendForhåndsvarselRequest> { body ->
                    personService.sjekkTilgangTilPerson(sakInfo.fnr, sakInfo.type).fold(
                        ifLeft = {
                            call.audit(sakInfo.fnr, AuditLogEvent.Action.SEARCH, revurdering.id.value)
                            call.svar(it.tilResultat())
                        },
                        ifRight = {
                            service.velgÅIkkeSendeForhåndsvarsel(
                                id = id,
                                begrunnelse = body.begrunnelse,
                                saksbehandler = call.suUserContext.saksbehandler,
                            ).fold(
                                ifLeft = { call.svar(it.tilResultat()) },
                                ifRight = {
                                    call.audit(sakInfo.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                                    call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toResponse())))
                                },
                            )
                        },
                    )
                }
            }
        }
        post("/{revurderingId}/underkjenn") {
            authorize(Brukerrolle.Attestant) {
                val id = call.parameters["revurderingId"].tilRevurderingId()
                    ?: return@authorize call.svar(ugyldigRevurderingId())
                val (sakInfo, eksisterende) = service.hentMedSakInfo(id)
                    ?: return@authorize call.svar(fantIkkeRevurdering())

                call.withBody<BegrunnelseRequest> { body ->
                    personService.sjekkTilgangTilPerson(sakInfo.fnr, sakInfo.type).fold(
                        ifLeft = {
                            call.audit(sakInfo.fnr, AuditLogEvent.Action.SEARCH, eksisterende.id.value)
                            call.svar(it.tilResultat())
                        },
                        ifRight = {
                            service.underkjenn(
                                id = id,
                                attestant = call.suUserContext.attestant,
                                begrunnelse = body.begrunnelse,
                            ).fold(
                                ifLeft = { call.svar(it.tilResultat()) },
                                ifRight = { oppdatert ->
                                    call.audit(sakInfo.fnr, AuditLogEvent.Action.UPDATE, oppdatert.id.value)
                                    call.svar(Resultat.json(HttpStatusCode.OK, serialize(oppdatert.toResponse())))
                                },
                            )
                        },
                    )
                }
            }
        }

        post("/{revurderingId}/attester") {
            authorize(Brukerrolle.Attestant) {
                val id = call.parameters["revurderingId"].tilRevurderingId()
                    ?: return@authorize call.svar(ugyldigRevurderingId())
                val (sakInfo, eksisterende) = service.hentMedSakInfo(id)
                    ?: return@authorize call.svar(fantIkkeRevurdering())

                personService.sjekkTilgangTilPerson(sakInfo.fnr, sakInfo.type).fold(
                    ifLeft = {
                        call.audit(sakInfo.fnr, AuditLogEvent.Action.SEARCH, eksisterende.id.value)
                        call.svar(it.tilResultat())
                    },
                    ifRight = {
                        service.attester(
                            id = id,
                            attestant = call.suUserContext.attestant,
                        ).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = { oppdatert ->
                                call.audit(sakInfo.fnr, AuditLogEvent.Action.UPDATE, oppdatert.id.value)
                                call.svar(
                                    Resultat.json(HttpStatusCode.OK, serialize(oppdatert.toResponse())),
                                )
                            },
                        )
                    },
                )
            }
        }
        post("/{revurderingId}/avslutt") {
            authorize(Brukerrolle.Saksbehandler) {
                val id = call.parameters["revurderingId"].tilRevurderingId()
                    ?: return@authorize call.svar(ugyldigRevurderingId())
                val (sakInfo, eksisterende) = service.hentMedSakInfo(id)
                    ?: return@authorize call.svar(fantIkkeRevurdering())

                call.withBody<BegrunnelseRequest> { body ->
                    personService.sjekkTilgangTilPerson(sakInfo.fnr, sakInfo.type).fold(
                        ifLeft = {
                            call.audit(sakInfo.fnr, AuditLogEvent.Action.SEARCH, eksisterende.id.value)
                            call.svar(it.tilResultat())
                        },
                        ifRight = {
                            service.avslutt(
                                id = id,
                                saksbehandler = call.suUserContext.saksbehandler,
                                begrunnelse = body.begrunnelse,
                            ).fold(
                                ifLeft = { call.svar(it.tilResultat()) },
                                ifRight = { oppdatert ->
                                    call.audit(sakInfo.fnr, AuditLogEvent.Action.UPDATE, oppdatert.id.value)
                                    call.svar(Resultat.json(HttpStatusCode.OK, serialize(oppdatert.toResponse())))
                                },
                            )
                        },
                    )
                }
            }
        }

        post("/{revurderingId}/iverksett") {
            authorize(Brukerrolle.Attestant) {
                val id = call.parameters["revurderingId"].tilRevurderingId()
                    ?: return@authorize call.svar(ugyldigRevurderingId())
                val (sakInfo, revurdering) = service.hentMedSakInfo(id)
                    ?: return@authorize call.svar(fantIkkeRevurdering())

                personService.sjekkTilgangTilPerson(sakInfo.fnr, sakInfo.type).fold(
                    ifLeft = {
                        call.audit(sakInfo.fnr, AuditLogEvent.Action.SEARCH, revurdering.id.value)
                        call.svar(it.tilResultat())
                    },
                    ifRight = {
                        call.audit(sakInfo.fnr, AuditLogEvent.Action.UPDATE, revurdering.id.value)
                        call.svar(
                            HttpStatusCode.Conflict.errorJson(
                                message = "Historiske Infotrygd-revurderinger kan ikke iverksettes før utbetalingsdesignet er avklart",
                                code = "historisk_infotrygd_utbetalingsdesign_ikke_avklart",
                            ),
                        )
                    },
                )
            }
        }
    }
}

private fun HistoriskInfotrygdRevurdering.toResponse() = HistoriskInfotrygdRevurderingResponse(
    id = id.value,
    sakId = sakId,
    periode = periode.toJson(),
    status = status.name,
    begrunnelse = begrunnelse,
    vedtaksbrevvalg = vedtaksbrevvalg.toResponseverdi(),
    vedtaksbrevFritekst = vedtaksbrevFritekst,
    kreverKontrollAvHistoriskForsørgingstillegg =
    kreverKontrollAvHistoriskForsørgingstillegg,
    harBekreftetKontrollAvHistoriskForsørgingstillegg =
    harBekreftetKontrollAvHistoriskForsørgingstillegg,
    forhåndsvarsel = forhåndsvarsel.toResponse(),
    sperregrunnerForAttestering = sperregrunnerForAttestering(),
    opprettet = opprettet.toString(),
    oppdatert = oppdatert.toString(),
)

private fun HistoriskInfotrygdRevurdering.sperregrunnerForAttestering(): List<String> = buildList {
    val gjeldendeBeregning = beregning
    if (gjeldendeBeregning == null) add("MANGLER_BEREGNING")
    if (gjeldendeBeregning != null && gjeldendeBeregning.månedsresultater.keys.toList() != periode.måneder()) {
        add("BEREGNING_DEKKER_IKKE_HELE_PERIODEN")
    }
    if (begrunnelse.isNullOrBlank()) add("MANGLER_BEGRUNNELSE")
    if (
        kreverKontrollAvHistoriskForsørgingstillegg &&
        !harBekreftetKontrollAvHistoriskForsørgingstillegg
    ) {
        add("MANGLER_BEKREFTELSE_AV_HISTORISK_FORSORGINGSTILLEGG")
    }
    if (!forhåndsvarsel.erGyldig()) add("MANGLER_GYLDIG_FORHANDSVARSEL")
    if (vedtaksbrevvalg == HistoriskInfotrygdVedtaksbrevvalg.IKKE_VALGT) {
        add("MANGLER_VEDTAKSBREVVALG")
    }
    if (
        vedtaksbrevvalg == HistoriskInfotrygdVedtaksbrevvalg.SEND &&
        vedtaksbrevFritekst.isNullOrBlank()
    ) {
        add("MANGLER_FRITEKST_TIL_VEDTAKSBREV")
    }
    if (
        vedtaksbrevvalg == HistoriskInfotrygdVedtaksbrevvalg.SEND &&
        beregning?.månedsresultater?.values?.let { resultater ->
            resultater.any {
                it is no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat.Ytelse
            } &&
                resultater.any {
                    it is no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat.Opphør
                }
        } == true
    ) {
        add("BLANDET_RESULTAT_MAA_BEHANDLES_SEPARAT")
    }
}

private fun HistoriskInfotrygdForhåndsvarsel.toResponse(): HistoriskInfotrygdForhåndsvarselResponse = when (this) {
    HistoriskInfotrygdForhåndsvarsel.IkkeValgt ->
        HistoriskInfotrygdForhåndsvarselResponse(
            status = "IKKE_VALGT",
            fritekst = null,
            begrunnelse = null,
            tidspunkt = null,
            erUtdatert = false,
        )
    is HistoriskInfotrygdForhåndsvarsel.IkkeSendt ->
        HistoriskInfotrygdForhåndsvarselResponse(
            status = "IKKE_SENDT",
            fritekst = null,
            begrunnelse = begrunnelse,
            tidspunkt = vurdert.toString(),
            erUtdatert = utdatert,
        )
    is HistoriskInfotrygdForhåndsvarsel.Sendt ->
        HistoriskInfotrygdForhåndsvarselResponse(
            status = "SENDT",
            fritekst = fritekst,
            begrunnelse = null,
            tidspunkt = sendt.toString(),
            erUtdatert = utdatert,
        )
}

private fun HistoriskInfotrygdMånedsgrunnlag.toResponse() =
    HistoriskInfotrygdMånedsgrunnlagResponse(
        revurderingId = revurdering.id.value,
        kreverKontrollAvHistoriskForsørgingstillegg =
        revurdering.kreverKontrollAvHistoriskForsørgingstillegg,
        harBekreftetKontrollAvHistoriskForsørgingstillegg =
        revurdering.harBekreftetKontrollAvHistoriskForsørgingstillegg,
        måneder = måneder.map { it.toResponse() },
        beregning = lagretBeregningResponse(),
    )

private fun HistoriskInfotrygdMånedsgrunnlag.lagretBeregningResponse(): HistoriskInfotrygdLagretBeregningResponse? {
    val beregning = revurdering.beregning ?: return null
    val gammeltBeløp = måneder.associate { måned ->
        måned.måned to when (måned) {
            is HistoriskInfotrygdMånedsgrunnlagForMåned.Ytelse -> måned.historiskBeløp
            is HistoriskInfotrygdMånedsgrunnlagForMåned.IngenYtelse -> BigDecimal.ZERO
        }
    }
    val resultater = beregning.månedsresultater.map { (måned, resultat) ->
        val nyttBeløp = when (resultat) {
            is no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat.Ytelse ->
                resultat.beløp
            is no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat.Opphør ->
                BigDecimal.ZERO
        }
        val gammelt = gammeltBeløp.getValue(måned)
        HistoriskInfotrygdBeregningForMånedResponse(
            måned = måned.toString(),
            satskategori = resultat.satskategori(),
            fradrag = resultat.fradrag().map { it.toResponse() },
            manueltOpphør = resultat.manueltOpphør(),
            gammeltBeløp = gammelt,
            nyttBeløp = nyttBeløp,
            differanse = nyttBeløp - gammelt,
            nyttResultat = when (resultat) {
                is no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat.Ytelse ->
                    "YTELSE"
                is no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat.Opphør ->
                    "OPPHØR"
            },
            opphørsgrunn =
            (resultat as? no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat.Opphør)
                ?.opphørsgrunn
                ?.name,
            begrunnelse =
            (resultat as? no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat.Opphør)
                ?.begrunnelse,
            gjeninnvilgelsesbegrunnelse =
            (resultat as? no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat.Ytelse)
                ?.gjeninnvilgelsesbegrunnelse,
        )
    }
    val harEtterbetaling = resultater.any { it.differanse.signum() > 0 }
    val harFeilutbetaling = resultater.any { it.differanse.signum() < 0 }
    return HistoriskInfotrygdLagretBeregningResponse(
        begrunnelse = requireNotNull(revurdering.begrunnelse),
        økonomiskRetning = when {
            harEtterbetaling -> "ETTERBETALING"
            harFeilutbetaling -> "FEILUTBETALING"
            else -> "INGEN_ENDRING"
        },
        måneder = resultater,
    )
}

private fun HistoriskInfotrygdRevurdertMånedsresultat.satskategori(): HistoriskInfotrygdSatskategori =
    when (this) {
        is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse -> bosituasjon.tilSatskategori()
        is HistoriskInfotrygdRevurdertMånedsresultat.Opphør -> bosituasjon.tilSatskategori()
    }

private fun HistoriskInfotrygdRevurdertMånedsresultat.fradrag(): List<FradragForMåned> =
    when (this) {
        is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse -> fradrag
        is HistoriskInfotrygdRevurdertMånedsresultat.Opphør -> fradrag
    }

private fun HistoriskInfotrygdRevurdertMånedsresultat.manueltOpphør(): HistoriskInfotrygdManueltOpphørResponse? =
    (this as? HistoriskInfotrygdRevurdertMånedsresultat.Opphør)
        ?.takeIf { it.begrunnelse != null }
        ?.let {
            HistoriskInfotrygdManueltOpphørResponse(
                opphørsgrunn = it.opphørsgrunn,
                begrunnelse = requireNotNull(it.begrunnelse),
            )
        }

private fun no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon.tilSatskategori(): HistoriskInfotrygdSatskategori =
    when (this) {
        no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon.ENSLIG ->
            HistoriskInfotrygdSatskategori.EN
        no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon.EPS_UNDER_67 ->
            HistoriskInfotrygdSatskategori.EU
        no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon.EPS_OVER_67 ->
            HistoriskInfotrygdSatskategori.EO
        no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon.ENSLIG_MED_BOFELLESSKAP ->
            HistoriskInfotrygdSatskategori.EV
    }

private fun FradragForMåned.toResponse() =
    HistoriskInfotrygdFradragForMånedResponse(
        type = fradragstype.kategori.name,
        beskrivelse = (fradragstype as? Fradragstype.Annet)?.beskrivelse,
        månedsbeløp = månedsbeløp,
        utenlandskInntekt = utenlandskInntekt?.let {
            HistoriskInfotrygdUtenlandskInntektRequest(
                beløpIUtenlandskValuta = it.beløpIUtenlandskValuta,
                valuta = it.valuta,
                kurs = it.kurs,
            )
        },
        tilhører = tilhører,
    )

private fun HistoriskInfotrygdMånedsgrunnlagForMåned.toResponse() = when (this) {
    is HistoriskInfotrygdMånedsgrunnlagForMåned.Ytelse ->
        HistoriskInfotrygdMånedsgrunnlagForMånedResponse(
            måned = måned.toString(),
            resultat = "YTELSE",
            stønadsstart = stønadsstart,
            opprinneligStønadId = opprinneligStønadId,
            opprinneligVedtakId = opprinneligVedtakId,
            oppdragId = oppdragId,
            kilde = kilde,
            historiskSats = historiskSats,
            historiskFradrag = historiskFradrag,
            historiskFradragskoder = historiskFradragskoder,
            historiskBeløp = historiskBeløp,
            foreslåttSatskategori = foreslåttSatskategori,
            kreverKontrollAvHistoriskForsørgingstillegg =
            kreverKontrollAvHistoriskForsørgingstillegg,
        )
    is HistoriskInfotrygdMånedsgrunnlagForMåned.IngenYtelse ->
        HistoriskInfotrygdMånedsgrunnlagForMånedResponse(
            måned = måned.toString(),
            resultat = "INGEN_YTELSE",
            stønadsstart = null,
            opprinneligStønadId = opprinneligStønadId,
            opprinneligVedtakId = opprinneligVedtakId,
            oppdragId = oppdragId,
            kilde = kilde,
            historiskSats = null,
            historiskFradrag = null,
            historiskFradragskoder = emptyList(),
            historiskBeløp = null,
            foreslåttSatskategori = null,
            kreverKontrollAvHistoriskForsørgingstillegg = false,
        )
}

private fun BeregnHistoriskInfotrygdRevurderingRequest.toCommand(): Either<String, BeregnHistoriskInfotrygdRevurderingCommand> {
    if (begrunnelse.isBlank()) return "Begrunnelse må fylles ut".left()
    val grunnlag = måneder.map { månedsgrunnlag ->
        val måned = try {
            no.nav.su.se.bakover.common.tid.periode.Måned.fra(YearMonth.parse(månedsgrunnlag.måned))
        } catch (_: DateTimeParseException) {
            return "Ugyldig måned: ${månedsgrunnlag.måned}".left()
        }
        val fradrag = månedsgrunnlag.fradrag.map { request ->
            val fradragstype = Fradragstype.tryParse(request.type, request.beskrivelse).fold(
                ifLeft = { return "Ugyldig fradragstype: ${request.type}".left() },
                ifRight = { it },
            )
            if (fradragstype.erSystemtype()) {
                return "Fradragstypen ${request.type} kan ikke velges manuelt".left()
            }
            val utenlandskInntekt = request.utenlandskInntekt?.let {
                UtenlandskInntekt.tryCreate(
                    beløpIUtenlandskValuta = it.beløpIUtenlandskValuta,
                    valuta = it.valuta,
                    kurs = it.kurs,
                ).fold(
                    ifLeft = { return "Ugyldig utenlandsk inntekt".left() },
                    ifRight = { it },
                )
            }
            try {
                FradragForMåned(
                    fradragstype = fradragstype,
                    månedsbeløp = request.månedsbeløp,
                    måned = måned,
                    utenlandskInntekt = utenlandskInntekt,
                    tilhører = request.tilhører,
                )
            } catch (_: IllegalArgumentException) {
                return "Fradrag kan ikke ha negativt månedsbeløp".left()
            }
        }
        try {
            no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdBeregningsgrunnlagForMåned(
                måned = måned,
                satskategori = månedsgrunnlag.satskategori,
                fradrag = fradrag,
                manueltOpphør = månedsgrunnlag.manueltOpphør?.let {
                    HistoriskInfotrygdManueltOpphør(
                        opphørsgrunn = it.opphørsgrunn,
                        begrunnelse = it.begrunnelse,
                    )
                },
                gjeninnvilgelsesbegrunnelse = månedsgrunnlag.gjeninnvilgelsesbegrunnelse,
            )
        } catch (exception: IllegalArgumentException) {
            return (exception.message ?: "Ugyldig beregningsgrunnlag").left()
        }
    }
    return BeregnHistoriskInfotrygdRevurderingCommand(
        begrunnelse = begrunnelse,
        månedsgrunnlag = grunnlag,
    ).right()
}

private fun Fradragstype.erSystemtype(): Boolean = when (this) {
    Fradragstype.ForventetInntekt,
    Fradragstype.AvkortingUtenlandsopphold,
    Fradragstype.BeregnetFradragEPS,
    Fradragstype.UnderMinstenivå,
    -> true
    else -> false
}

private fun HistoriskInfotrygdBeregningResultat.toResponse() =
    HistoriskInfotrygdBeregningResponse(
        behandling = requireNotNull(revurdering).toResponse(),
        økonomiskRetning = økonomiskRetning.name,
        måneder = måneder.map {
            HistoriskInfotrygdBeregningForMånedResponse(
                måned = it.måned.toString(),
                satskategori = it.satskategori,
                fradrag = it.fradrag.map { fradrag -> fradrag.toResponse() },
                manueltOpphør = it.manueltOpphør?.let { opphør ->
                    HistoriskInfotrygdManueltOpphørResponse(
                        opphørsgrunn = opphør.opphørsgrunn,
                        begrunnelse = opphør.begrunnelse,
                    )
                },
                gammeltBeløp = it.gammeltBeløp,
                nyttBeløp = it.nyttBeløp,
                differanse = it.differanse,
                nyttResultat = it.nyttResultat,
                opphørsgrunn = it.opphørsgrunn,
                begrunnelse = it.begrunnelse,
                gjeninnvilgelsesbegrunnelse = it.gjeninnvilgelsesbegrunnelse,
            )
        },
    )

private fun HistoriskInfotrygdVedtaksbrevvalg.toResponseverdi(): String = when (this) {
    HistoriskInfotrygdVedtaksbrevvalg.IKKE_VALGT -> "IKKE_VALGT"
    HistoriskInfotrygdVedtaksbrevvalg.SEND -> "SEND"
    HistoriskInfotrygdVedtaksbrevvalg.IKKE_SEND -> "IKKE_SEND"
}

private fun String?.tilRevurderingId(): HistoriskInfotrygdRevurderingId? =
    this?.let { runCatching { HistoriskInfotrygdRevurderingId(UUID.fromString(it)) }.getOrNull() }

private fun ugyldigRevurderingId() = HttpStatusCode.BadRequest.errorJson(
    message = "Ugyldig revurderings-ID",
    code = "ugyldig_historisk_infotrygd_revurdering_id",
)

private fun fantIkkeRevurdering() = HttpStatusCode.NotFound.errorJson(
    message = "Fant ikke historisk Infotrygd-revurdering",
    code = "historisk_infotrygd_revurdering_ikke_funnet",
)

private fun KunneIkkeOppretteHistoriskInfotrygdRevurderingService.tilResultat(): Resultat = when (this) {
    KunneIkkeOppretteHistoriskInfotrygdRevurderingService.FantIngenFullførtHistoriskProjeksjon ->
        HttpStatusCode.NotFound.errorJson(
            message = "Fant ingen fullført historisk projeksjon for personen",
            code = "historisk_infotrygd_projeksjon_ikke_funnet",
        )

    KunneIkkeOppretteHistoriskInfotrygdRevurderingService.PeriodenMåBeståAvHeleMåneder ->
        HttpStatusCode.BadRequest.errorJson(
            message = "Perioden må starte første dag i en måned og slutte siste dag i en måned",
            code = "historisk_infotrygd_perioden_maa_bestaa_av_hele_maaneder",
        )

    is KunneIkkeOppretteHistoriskInfotrygdRevurderingService.MånedManglerHistoriskVedtak ->
        HttpStatusCode.UnprocessableEntity.errorJson(
            message = "Måneden $måned ligger ikke i et historisk vedtak",
            code = "historisk_infotrygd_maaned_mangler_vedtak",
        )

    is KunneIkkeOppretteHistoriskInfotrygdRevurderingService.OverlapperInnvilgetSuAppYtelse ->
        HttpStatusCode.Conflict.errorJson(
            message = "Perioden overlapper innvilget SU-app-ytelse fra $førsteInnvilgedeMåned",
            code = "historisk_infotrygd_overlapper_su_app",
        )

    is KunneIkkeOppretteHistoriskInfotrygdRevurderingService.UgyldigHistoriskGrunnlag ->
        HttpStatusCode.UnprocessableEntity.errorJson(
            message = "Det historiske grunnlaget kan ikke brukes: $begrunnelse",
            code = "ugyldig_historisk_infotrygd_grunnlag",
        )

    is KunneIkkeOppretteHistoriskInfotrygdRevurderingService.LagringFeilet -> when (val årsak = feil) {
        is KunneIkkeOppretteHistoriskInfotrygdRevurdering.OverlapperÅpenBehandling ->
            Resultat.json(
                HttpStatusCode.Conflict,
                serialize(
                    OverlappendeHistoriskInfotrygdRevurderingResponse(
                        message = "Perioden overlapper en åpen historisk revurdering",
                        code = "historisk_infotrygd_revurdering_overlapper_aapen_behandling",
                        eksisterendeRevurderingId = årsak.eksisterendeRevurderingId.value,
                        sakId = årsak.sakId,
                    ),
                ),
            )

        KunneIkkeOppretteHistoriskInfotrygdRevurdering.FeilProjeksjon,
        is KunneIkkeOppretteHistoriskInfotrygdRevurdering.ManglerGjeldendeData,
        -> HttpStatusCode.InternalServerError.errorJson(
            message = "Kunne ikke lagre historisk Infotrygd-revurdering",
            code = "kunne_ikke_lagre_historisk_infotrygd_revurdering",
        )
    }
}

private fun KunneIkkeEndreHistoriskInfotrygdRevurdering.tilResultat(): Resultat = when (this) {
    KunneIkkeEndreHistoriskInfotrygdRevurdering.FantIkkeBehandling -> fantIkkeRevurdering()
    is KunneIkkeEndreHistoriskInfotrygdRevurdering.UgyldigTilstand ->
        HttpStatusCode.Conflict.errorJson(
            message = "Behandlingen kan ikke endres i gjeldende tilstand: $begrunnelse",
            code = "historisk_infotrygd_revurdering_ugyldig_tilstand",
        )
}

private fun KunneIkkeBeregneHistoriskInfotrygdRevurderingService.tilResultat(): Resultat = when (this) {
    KunneIkkeBeregneHistoriskInfotrygdRevurderingService.FantIkkeBehandling ->
        fantIkkeRevurdering()
    is KunneIkkeBeregneHistoriskInfotrygdRevurderingService.UgyldigGrunnlag ->
        HttpStatusCode.UnprocessableEntity.errorJson(
            message = "Beregningsgrunnlaget kan ikke brukes: $begrunnelse",
            code = "ugyldig_historisk_infotrygd_beregningsgrunnlag",
        )
}

private fun KunneIkkeLageHistoriskInfotrygdForhåndsvarsel.tilResultat(): Resultat = when (this) {
    KunneIkkeLageHistoriskInfotrygdForhåndsvarsel.FantIkkeBehandling -> fantIkkeRevurdering()
    KunneIkkeLageHistoriskInfotrygdForhåndsvarsel.ManglerBeregning ->
        HttpStatusCode.Conflict.errorJson(
            message = "Behandlingen må beregnes før forhåndsvarselet kan lages",
            code = "historisk_infotrygd_forhandsvarsel_mangler_beregning",
        )
    KunneIkkeLageHistoriskInfotrygdForhåndsvarsel.ManglerFritekst ->
        HttpStatusCode.BadRequest.errorJson(
            message = "Fritekst må fylles ut",
            code = "historisk_infotrygd_forhandsvarsel_mangler_fritekst",
        )
    is KunneIkkeLageHistoriskInfotrygdForhåndsvarsel.KunneIkkeGenererePdf ->
        HttpStatusCode.InternalServerError.errorJson(
            message = "Kunne ikke generere forhåndsvarselet",
            code = "historisk_infotrygd_forhandsvarsel_pdf_feilet",
        )
}

private fun KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel.tilResultat(): Resultat = when (this) {
    KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel.FantIkkeBehandling -> fantIkkeRevurdering()
    KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel.UventetDokumenttype ->
        HttpStatusCode.InternalServerError.errorJson(
            message = "Forhåndsvarselet fikk feil dokumenttype",
            code = "historisk_infotrygd_forhandsvarsel_feil_dokumenttype",
        )
    is KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel.UgyldigTilstand ->
        HttpStatusCode.Conflict.errorJson(
            message = "Forhåndsvarselet kan ikke sendes: $begrunnelse",
            code = "historisk_infotrygd_forhandsvarsel_ugyldig_tilstand",
        )
    is KunneIkkeSendeHistoriskInfotrygdForhåndsvarsel.KunneIkkeGenererePdf ->
        HttpStatusCode.InternalServerError.errorJson(
            message = "Kunne ikke generere forhåndsvarselet",
            code = "historisk_infotrygd_forhandsvarsel_pdf_feilet",
        )
}

private fun KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast.tilResultat(): Resultat = when (this) {
    KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast.FantIkkeBehandling -> fantIkkeRevurdering()
    KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast.SkalIkkeSendeBrev ->
        HttpStatusCode.Conflict.errorJson(
            message = "Vedtaksbrevvalget er ikke SEND",
            code = "historisk_infotrygd_vedtaksbrev_skal_ikke_sendes",
        )
    is KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast.KunneIkkeLageBrevgrunnlag ->
        HttpStatusCode.UnprocessableEntity.errorJson(
            message = "Behandlingen kan ikke uttrykkes korrekt med dagens revurderingsbrev: $feil",
            code = when (feil) {
                KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando.ManglerBeregning ->
                    "historisk_infotrygd_vedtaksbrev_mangler_beregning"
                KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando.ManglerFritekst ->
                    "historisk_infotrygd_vedtaksbrev_mangler_fritekst"
                KunneIkkeLageHistoriskInfotrygdVedtaksbrevkommando
                    .BlandetYtelseOpphørOgGjeninnvilgelseStøttesIkkeAvBrevmalen,
                ->
                    "historisk_infotrygd_vedtaksbrev_blandet_resultat_ikke_stoettet"
            },
        )
    is KunneIkkeLageHistoriskInfotrygdVedtaksbrevutkast.KunneIkkeGenererePdf ->
        HttpStatusCode.InternalServerError.errorJson(
            message = "Kunne ikke generere vedtaksbrevutkast",
            code = "historisk_infotrygd_vedtaksbrev_pdf_feilet",
        )
}
