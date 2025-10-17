package tilbakekreving.application.service.opprett

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.statistikk.GenerellSakStatistikk
import tilbakekreving.application.service.statistikk.toTilbakeStatistikkOpprettet
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandling
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.opprettelse.KunneIkkeOppretteTilbakekrevingsbehandling
import tilbakekreving.domain.opprettelse.OpprettTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.opprettelse.opprettTilbakekrevingsbehandling
import tilgangstyring.application.TilgangstyringService
import java.time.Clock

class OpprettTilbakekrevingsbehandlingService(
    private val sessionFactory: SessionFactory,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val tilgangstyring: TilgangstyringService,
    private val sakService: SakService,
    private val sakStatistikk: SakStatistikkRepo,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun opprett(
        command: OpprettTilbakekrevingsbehandlingCommand,
    ): Either<KunneIkkeOppretteTilbakekrevingsbehandling, OpprettetTilbakekrevingsbehandling> {
        val sakId = command.sakId
        tilgangstyring.assertHarTilgangTilSak(sakId).onLeft {
            log.info("Kunne ikke opprette tilbakekreving. Mangler tilgang til sak. command: $command. Feil: $it")
            return KunneIkkeOppretteTilbakekrevingsbehandling.IkkeTilgang(it).left()
        }
        val sak = sakService.hentSak(sakId).getOrElse {
            throw IllegalStateException("Kunne ikke opprette tilbakekreving. Fant ikke sak $sakId. Feil: $it")
        }
        if (sak.versjon != command.klientensSisteSaksversjon) {
            log.info("Opprettelse av tilbakekreving - Sakens versjon (${sak.versjon}) er ulik saksbehandlers versjon. Command: $command")
        }
        if (sak.behandlinger.tilbakekrevinger.harÅpen()) {
            log.info("Kunne ikke opprette tilbakekreving. Fant allerede en åpen tilbakekrevingsbehandling for sak $sakId")
            return KunneIkkeOppretteTilbakekrevingsbehandling.FinnesAlleredeEnÅpenBehandling.left()
        }

        return when (val k = sak.uteståendeKravgrunnlag) {
            null -> KunneIkkeOppretteTilbakekrevingsbehandling.IngenUteståendeKravgrunnlag.left()
            else -> opprettTilbakekrevingsbehandling(
                command = command,
                forrigeVersjon = sak.versjon,
                clock = clock,
                fnr = sak.fnr,
                kravgrunnlag = k,
                erKravgrunnlagUtdatert = false,
            ).let { (hendelse, opprettetBehandling) ->
                sessionFactory.withTransactionContext { tx ->
                    tilbakekrevingsbehandlingRepo.lagre(hendelse, command.toDefaultHendelsesMetadata(), tx)
                    sakStatistikk.lagreSakStatistikk(
                        opprettetBehandling.toTilbakeStatistikkOpprettet(
                            GenerellSakStatistikk.create(
                                clock = clock,
                                sak = sak,
                            ),
                        ),
                        sessionContext = tx,
                    )
                    opprettetBehandling.right()
                }
            }
        }
    }
}
