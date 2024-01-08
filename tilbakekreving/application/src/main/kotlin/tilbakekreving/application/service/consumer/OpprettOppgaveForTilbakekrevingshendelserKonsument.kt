package tilbakekreving.application.service.consumer

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.extensions.mapOneIndexed
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseMetadata
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import org.slf4j.LoggerFactory
import person.domain.PersonService
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.infrastructure.repo.OpprettetTilbakekrevingsbehandlingHendelsestype
import java.time.Clock
import java.util.UUID

class OpprettOppgaveForTilbakekrevingshendelserKonsument(
    private val sakService: SakService,
    private val personService: PersonService,
    private val oppgaveService: OppgaveService,
    private val tilbakekrevingsbehandlingHendelseRepo: TilbakekrevingsbehandlingRepo,
    private val oppgaveHendelseRepo: OppgaveHendelseRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : Hendelseskonsument {
    override val konsumentId = HendelseskonsumentId("OpprettOppgaveForTilbakekrevingsbehandlingHendelser")

    private val log = LoggerFactory.getLogger(this::class.java)

    fun opprettOppgaver(correlationId: CorrelationId) {
        Either.catch {
            hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
                konsumentId = konsumentId,
                hendelsestype = OpprettetTilbakekrevingsbehandlingHendelsestype,
            ).forEach { (sakId, hendelsesIder) ->
                prosesserSak(sakId, hendelsesIder, correlationId)
            }
        }.mapLeft {
            log.error(
                "Kunne ikke opprette oppgave(r) for tilbakekrevingsbehandling: Det ble kastet en exception for konsument $konsumentId",
                it,
            )
        }
    }

    private fun prosesserSak(
        sakId: UUID,
        hendelsesIder: Nel<HendelseId>,
        correlationId: CorrelationId,
    ) {
        log.info("Starter opprettelse av oppgaver for opprettetTilbakekrevingsbehandling-hendelser på sak $sakId. Hendelsesider: $hendelsesIder")

        val sak = sakService.hentSak(sakId)
            .getOrElse { throw IllegalStateException("Kunne ikke hente sakInfo $sakId for å opprette oppgave for OpprettetTilbakekrevingshendelse") }

        hendelsesIder.mapOneIndexed { idx, relatertHendelsesId ->
            val relatertHendelse = tilbakekrevingsbehandlingHendelseRepo.hentHendelse(relatertHendelsesId)
                ?: return@mapOneIndexed Unit.also { log.error("Feil ved henting av hendelse for å opprette oppgave. sak $sakId, hendelse $relatertHendelsesId") }

            oppgaveHendelseRepo.hentHendelseForRelatert(relatertHendelse.hendelseId, sak.id)?.let {
                return@mapOneIndexed Unit.also {
                    hendelsekonsumenterRepo.lagre(relatertHendelse.hendelseId, konsumentId)
                    log.error("Feil ved oppretting av oppgave for tilbakekreving ${relatertHendelse.id.value}. Oppgave allerede opprettet for hendelse ${relatertHendelse.hendelseId}. Konsumenten vil lagre denne hendelsen")
                }
            }

            val nesteVersjon = sak.versjon.inc(idx)
            opprettEksternOppgaveOgLagHendelse(
                relaterteHendelse = relatertHendelse.hendelseId,
                // TODO tilbakekreving jah: Kanskje vi heller skal gå tilbake til å hente versjonen per iterasjon? Vi øker risikoen for å opprette en oppgave, men ikke klare lagre hendelsen.
                nesteVersjon = nesteVersjon,
                sakInfo = sak.info(),
                correlationId = correlationId,
                tilordnetRessurs = relatertHendelse.utførtAv as NavIdentBruker.Saksbehandler,
            ).map {
                sessionFactory.withTransactionContext { context ->
                    oppgaveHendelseRepo.lagre(it.first, it.second, context)
                    hendelsekonsumenterRepo.lagre(relatertHendelse.hendelseId, konsumentId, context)
                }
            }.mapLeft {
                log.error("Feil skjedde ved oppretting av oppgave for OpprettetTilbakekrevingsbehandlinghendelse $it. For sak $sakId, hendelse ${relatertHendelse.id}")
            }
        }
    }

    private fun opprettEksternOppgaveOgLagHendelse(
        relaterteHendelse: HendelseId,
        nesteVersjon: Hendelsesversjon,
        sakInfo: SakInfo,
        correlationId: CorrelationId,
        tilordnetRessurs: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeOppretteOppgave, Pair<OppgaveHendelse, OppgaveHendelseMetadata>> {
        val aktørId = personService.hentAktørIdMedSystembruker(sakInfo.fnr).getOrElse {
            return KunneIkkeOppretteOppgave.FeilVedHentingAvPerson(it).left()
        }

        val oppgaveResponse = oppgaveService.opprettOppgaveMedSystembruker(
            OppgaveConfig.Tilbakekrevingsbehandling(
                saksnummer = sakInfo.saksnummer,
                aktørId = aktørId,
                tilordnetRessurs = tilordnetRessurs,
                clock = clock,
            ),
        ).getOrElse {
            return KunneIkkeOppretteOppgave.FeilVedOpprettelseAvOppgave.left()
        }

        return Pair(
            OppgaveHendelse.Opprettet(
                hendelseId = HendelseId.generer(),
                hendelsestidspunkt = Tidspunkt.now(clock),
                oppgaveId = oppgaveResponse.oppgaveId,
                versjon = nesteVersjon,
                sakId = sakInfo.sakId,
                relaterteHendelser = listOf(relaterteHendelse),
                beskrivelse = oppgaveResponse.beskrivelse,
                oppgavetype = oppgaveResponse.oppgavetype,
            ),
            OppgaveHendelseMetadata(
                correlationId = correlationId,
                ident = null,
                brukerroller = listOf(),
                request = oppgaveResponse.request,
                response = oppgaveResponse.response,
            ),
        ).right()
    }
}
