package no.nav.su.se.bakover.web.routes.behandling

import arrow.core.Either
import arrow.core.flatMap
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post

import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.VedtakInnhold
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.lesUUID
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.toUUID
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.format.DateTimeFormatter.ofPattern

internal const val behandlingPath = "$sakPath/{sakId}/behandlinger"

internal fun Route.behandlingRoutes(
    repo: ObjectRepo,
    pdf: PdfGenerator
) {
    val log = LoggerFactory.getLogger(this::class.java)

    data class OpprettBehandlingBody(val soknadId: String)

    post(behandlingPath) {
        call.lesUUID("sakId").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { sakId ->
                Either.catch { deserialize<OpprettBehandlingBody>(call) }
                    .flatMap { it.soknadId.toUUID() }
                    .fold(
                        ifLeft = { call.svar(BadRequest.message("Ugyldig body")) },
                        ifRight = { søknadId ->
                            when (val sak = repo.hentSak(sakId)) {
                                null -> call.svar(NotFound.message("Fant ikke sak med id:$sakId"))
                                else -> {
                                    call.audit("Oppretter behandling på sak: $sakId og søknadId: $søknadId")
                                    val behandling = sak.opprettSøknadsbehandling(søknadId)
                                    call.svar(Created.jsonBody(behandling))
                                }
                            }
                        }
                    )
            }
        )
    }

    get("$behandlingPath/{behandlingId}") {
        call.lesUUID("behandlingId").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { id ->
                call.audit("Henter behandling med id: $id")
                when (val behandling = repo.hentBehandling(id)) {
                    null -> call.svar(NotFound.message("Fant ikke behandling med id:$id"))
                    else -> call.svar(OK.jsonBody(behandling))
                }
            }
        )
    }

    data class OpprettBeregningBody(
        val fom: LocalDate,
        val tom: LocalDate,
        val sats: String,
        val fradrag: List<FradragJson>
    ) {
        fun valid() = fom.dayOfMonth == 1 &&
            tom.dayOfMonth == tom.lengthOfMonth() &&
            (sats == Sats.HØY.name || sats == Sats.LAV.name) &&
            fradrag.all { Fradragstype.isValid(it.type) }
    }
    post("$behandlingPath/{behandlingId}/beregn") {
        call.lesUUID("behandlingId").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { behandlingId ->
                Either.catch { deserialize<OpprettBeregningBody>(call) }.fold(
                    ifLeft = {
                        log.info("Ugyldig behandling-body: ", it)
                        call.svar(BadRequest.message("Ugyldig body"))
                    },
                    ifRight = { body ->
                        if (body.valid()) {
                            when (val behandling = repo.hentBehandling(behandlingId)) {
                                null -> call.svar(NotFound.message("Fant ikke behandling med id:$behandlingId"))
                                else -> call.svar(
                                    Created.jsonBody(
                                        behandling.opprettBeregning(
                                            fom = body.fom,
                                            tom = body.tom,
                                            sats = Sats.valueOf(body.sats),
                                            fradrag = body.fradrag.map { it.toFradrag() }
                                        )
                                    )
                                )
                            }
                        } else {
                            call.svar(BadRequest.message("Ugyldige input-parametere for: $body"))
                        }
                    }
                )
            }
        )
    }

    get("$behandlingPath/{behandlingId}/vedtaksutkast") {
        call.lesUUID("behandlingId").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { id ->
                when (val behandling = repo.hentBehandling(id)) {
                    null -> call.svar(NotFound.message("Fant ikke behandling med id:$id"))
                    else -> {
                        val personalia = behandling.toDto().søknad.søknadInnhold.personopplysninger
                        pdf.genererPdf(
                            VedtakInnhold(
                                dato = now().format(ofPattern("dd.MM.yyyy")),
                                fødselsnummer = personalia.fnr,
                                fornavn = personalia.fornavn,
                                etternavn = personalia.etternavn,
                                adresse = personalia.gateadresse,
                                postnummer = personalia.postnummer,
                                poststed = personalia.poststed
                            )
                        ).fold(
                            ifLeft = { call.svar(InternalServerError.message("Kunne ikke generere pdf")) },
                            ifRight = {
                                call.respondBytes(it, ContentType.Application.Pdf)
                            }
                        )
                    }
                }
            }
        )
    }
}
