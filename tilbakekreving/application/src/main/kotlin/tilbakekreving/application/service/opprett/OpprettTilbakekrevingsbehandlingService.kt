package tilbakekreving.application.service.opprett

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.common.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandling
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.opprett.KunneIkkeOppretteTilbakekrevingsbehandling
import tilbakekreving.domain.opprett.OpprettTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.opprett.opprettTilbakekrevingsbehandling
import java.time.Clock

class OpprettTilbakekrevingsbehandlingService(
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val sakService: SakService,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val oppgaveHendelseRepo: OppgaveHendelseRepo,
    private val sessionFactory: SessionFactory,
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
        if (sak.behandlinger.tilbakekrevinger.harÅpen()) {
            log.info("Kunne ikke opprette tilbakekreving. Fant allerede en åpen tilbakekrevingsbehandling for sak $sakId")
            return KunneIkkeOppretteTilbakekrevingsbehandling.FinnesAlleredeEnÅpenBehandling.left()
        }
        return when (val k = sak.uteståendeKravgrunnlag) {
            null -> KunneIkkeOppretteTilbakekrevingsbehandling.IngenÅpneKravgrunnlag.left()
            else -> opprettTilbakekrevingsbehandling(
                command = command,
                forrigeVersjon = sak.versjon,
                clock = clock,
                kravgrunnlag = k,
            ).let { (hendelse, behandling) ->
                val oppgaveHendelse = opprettOppgaveHendelse(hendelse, sak, command).getOrElse { return it.left() }

                sessionFactory.withTransactionContext {
                    tilbakekrevingsbehandlingRepo.lagre(hendelse, it)
                    oppgaveHendelseRepo.lagre(oppgaveHendelse, it)
                }
                behandling.right()
            }
        }
    }

    private fun opprettOppgaveHendelse(
        relaterteHendelse: OpprettetTilbakekrevingsbehandlingHendelse,
        sak: Sak,
        command: OpprettTilbakekrevingsbehandlingCommand,
    ): Either<KunneIkkeOppretteTilbakekrevingsbehandling, OppgaveHendelse> {
        val aktørId = personService.hentAktørId(sak.fnr).getOrElse {
            return KunneIkkeOppretteTilbakekrevingsbehandling.FeilVedHentingAvPerson(it).left()
        }

        val oppgaveId = oppgaveService.opprettOppgave(
            OppgaveConfig.Tilbakekrevingsbehandling(
                saksnummer = sak.saksnummer,
                aktørId = aktørId,
                tilordnetRessurs = command.opprettetAv,
                clock = clock,
            ),
        ).getOrElse {
            return KunneIkkeOppretteTilbakekrevingsbehandling.FeilVedOpprettelseAvOppgave.left()
        }

        return OppgaveHendelse.opprettet(
            hendelseId = HendelseId.generer(),
            hendelsestidspunkt = Tidspunkt.now(clock),
            oppgaveId = oppgaveId,
            versjon = relaterteHendelse.versjon.inc(),
            sakId = sak.id,
            relaterteHendelser = listOf(relaterteHendelse.hendelseId),
            meta = DefaultHendelseMetadata(
                correlationId = command.correlationId,
                ident = command.opprettetAv,
                brukerroller = command.brukerroller,
            ),
        ).right()
    }
}
