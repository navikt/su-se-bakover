package no.nav.su.se.bakover.web.external

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.sak.OpprettOppgaveDersomAktueltUførevedtakCommand
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.UførevedtakBehandlingstype

/**
 * Laget slik at uføre kan sende inn et fnr og tilhørende vedtaksperiode. Så svarer vi om fnr har supplerende stønad i den perioden.
 */
internal fun Route.pensjonOgUføreRoutes(
    sakService: SakService,
) {
    /**
     * Alle verdiene er knyttet til pensjon sitt vedtak.
     * Bør tydeliggjøres i domenekoden til SU.
     *
     */
    data class Body(
        val sakId: String,
        val vedtakId: String,
        val vedtakstype: String,
        val fnr: String,
        val vedtaksperiode: PeriodeMedOptionalTilOgMedJson,
        val behandlingstype: String,
    ) {
        fun toDomain(): Either<Resultat, OpprettOppgaveDersomAktueltUførevedtakCommand> {
            val behandlingstype: UførevedtakBehandlingstype = when (this.behandlingstype) {
                "AUTO" -> UførevedtakBehandlingstype.AUTOMATISK
                "DEL_AUTO" -> UførevedtakBehandlingstype.DELVIS_AUTOMATISK
                "MAN" -> UførevedtakBehandlingstype.MANUELL
                else -> return HttpStatusCode.BadRequest.errorJson(
                    message = "Ukjent behandlingstype. Forventet AUTO, DEL_AUTO eller MAN.",
                    code = "ukjent_behandlingstype",
                ).left()
            }
            return OpprettOppgaveDersomAktueltUførevedtakCommand(
                fnr = Fnr(this.fnr),
                periode = this.vedtaksperiode.toDomain(),
                uføreSakId = this.sakId,
                uføreVedtakId = this.vedtakId,
                uføreVedtakstype = this.vedtakstype,
                behandlingstype = behandlingstype,
            ).right()
        }
    }
    post("/pensjon/vedtakshendelse") {
        this.call.withBody<Body> { body ->
            body.toDomain().fold(
                { resultat -> call.svar(resultat) },
                { command ->
                    call.svar(Resultat.accepted())
                    CoroutineScope(Dispatchers.IO).launch { sakService.opprettOppgaveDersomAktueltUførevedtak(command) }
                },
            )
        }
    }
}
