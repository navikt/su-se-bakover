package tilbakekreving.application.service.consumer

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import org.slf4j.LoggerFactory
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.infrastructure.OpprettTilbakekrevingsbehandlingHendelsestype
import java.time.Clock
import java.util.UUID

class OpprettOppgaveForTilbakekrevingshendelserKonsument(
    private val sakService: SakService,
    private val personService: PersonService,
    private val oppgaveService: OppgaveService,
    private val tilbakekrevingsbehandlingHendelseRepo: TilbakekrevingsbehandlingRepo,
    private val oppgaveHendelseRepo: OppgaveHendelseRepo,
    private val hendelseRepo: HendelseRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : Hendelseskonsument {
    override val konsumentId = HendelseskonsumentId("OpprettOppgaveForTilbakekrevingsbehandlingHendelser")

    private val log = LoggerFactory.getLogger(this::class.java)

    fun opprettOppgaver(correlationId: CorrelationId) {
        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = OpprettTilbakekrevingsbehandlingHendelsestype,
        ).forEach { (sakId, hendelsesIder) ->
            prosesserSak(sakId, hendelsesIder, correlationId)
        }
    }

    private fun prosesserSak(
        sakId: UUID,
        hendelsesIder: Nel<HendelseId>,
        correlationId: CorrelationId,
    ) {
        log.info("starter opprettelse av oppgaver for opprettetTilbakekrevingsbehandling-hendelser på sak $sakId")

        val sak = sakService.hentSak(sakId)
            .getOrElse { throw IllegalStateException("Kunne ikke hente sakInfo $sakId for å opprette oppgave for OpprettetTilbakekrevingshendelse") }

        hendelsesIder.map { relatertHendelsesId ->
            val nesteVersjon = hendelseRepo.hentSisteVersjonFraEntitetId(sak.id)?.inc()
                ?: throw IllegalStateException("Kunne ikke hente siste versjon for sak ${sak.id} for å opprette oppgave")

            val relatertHendelse = tilbakekrevingsbehandlingHendelseRepo.hentHendelse(relatertHendelsesId)
                ?: throw IllegalStateException("Feil ved henting av hendelse for å opprette oppgave. sak $sakId, hendelse $relatertHendelsesId")

            opprettOppgaveHendelse(
                relaterteHendelse = relatertHendelse.hendelseId,
                nesteVersjon = nesteVersjon,
                sakInfo = sak.info(),
                correlationId = correlationId,
                tilordnetRessurs = relatertHendelse.meta.ident as NavIdentBruker.Saksbehandler,
            ).map {
                sessionFactory.withTransactionContext { context ->
                    oppgaveHendelseRepo.lagre(it, context)
                    hendelsekonsumenterRepo.lagre(relatertHendelse.hendelseId, konsumentId, context)
                }
            }.mapLeft {
                log.error("Feil skjedde ved oppretting av oppgave for OpprettetTilbakekrevingsbehandlinghendelse $it. For sak $sakId, hendelse ${relatertHendelse.id}")
            }
        }
    }

    private fun opprettOppgaveHendelse(
        relaterteHendelse: HendelseId,
        nesteVersjon: Hendelsesversjon,
        sakInfo: SakInfo,
        correlationId: CorrelationId,
        tilordnetRessurs: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeOppretteOppgave, OppgaveHendelse> {
        val aktørId = personService.hentAktørIdMedSystembruker(sakInfo.fnr).getOrElse {
            return KunneIkkeOppretteOppgave.FeilVedHentingAvPerson(it).left()
        }

        val oppgaveId = oppgaveService.opprettOppgaveMedSystembruker(
            OppgaveConfig.Tilbakekrevingsbehandling(
                saksnummer = sakInfo.saksnummer,
                aktørId = aktørId,
                tilordnetRessurs = tilordnetRessurs,
                clock = clock,
            ),
        ).getOrElse {
            return KunneIkkeOppretteOppgave.FeilVedOpprettelseAvOppgave.left()
        }

        return OppgaveHendelse.opprettet(
            hendelseId = HendelseId.generer(),
            hendelsestidspunkt = Tidspunkt.now(clock),
            oppgaveId = oppgaveId,
            versjon = nesteVersjon,
            sakId = sakInfo.sakId,
            relaterteHendelser = listOf(relaterteHendelse),
            meta = DefaultHendelseMetadata.fraCorrelationId(correlationId = correlationId),
        ).right()
    }
}
