package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.behandling.FradragJson
import no.nav.su.se.bakover.web.routes.behandling.enumContains
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withSakId
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

internal const val revurderingPath = "$sakPath/{sakId}/revurdering"

@KtorExperimentalAPI
internal fun Route.revurderingRoutes(
    revurderingService: RevurderingService
) {
    val log = LoggerFactory.getLogger(this::class.java)

    data class OpprettBeregningBody(
        val behandlingId: UUID,
        val fraOgMed: LocalDate,
        val tilOgMed: LocalDate,
        val fradrag: List<FradragJson>
    ) {
        fun valid() = fraOgMed.dayOfMonth == 1 &&
            tilOgMed.dayOfMonth == tilOgMed.lengthOfMonth() &&
            fradrag.all {
                Fradragstype.isValid(it.type) &&
                    enumContains<FradragTilhører>(it.tilhører) &&
                    it.utenlandskInntekt?.isValid() ?: true
            }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/beregnOgSimuler") {
            call.withSakId { sakId ->
                Either.catch { deserialize<OpprettBeregningBody>(call) }.fold(
                    ifLeft = {
                        log.info("Ugyldig behandling-body: ", it)
                        call.svar(BadRequest.message("Ugyldig body"))
                    },
                    ifRight = { body ->
                        if (body.valid()) {
                            revurderingService.beregnOgSimuler(
                                sakId = sakId,
                                behandlingId = body.behandlingId,
                                saksbehandler = Saksbehandler(call.suUserContext.getNAVIdent()),
                                fraOgMed = body.fraOgMed,
                                tilOgMed = body.tilOgMed,
                                fradrag = body.fradrag.map { it.toFradrag(Periode(body.fraOgMed, body.tilOgMed)) }
                            ).fold(
                                ifLeft = {
                                    //TODO
                                    call.svar(BadRequest.message("noe gikk feil"))
                                },
                                ifRight = {
                                    call.audit("Opprettet en ny revurdering beregning og simulering på sak med id $sakId")
                                    call.svar(Resultat.json(OK, serialize(it.toJson())))
                                },
                            )
                        } else {
                            call.svar(BadRequest.message("Ugyldige input-parametere for: $body"))
                        }

                    }
                )
            }
        }
    }
}
