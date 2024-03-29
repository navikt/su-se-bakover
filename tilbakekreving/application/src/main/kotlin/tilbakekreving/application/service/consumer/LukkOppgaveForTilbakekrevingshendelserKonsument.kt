package tilbakekreving.application.service.consumer

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.extensions.mapOneIndexed
import no.nav.su.se.bakover.common.extensions.pickByCondition
import no.nav.su.se.bakover.common.extensions.whenever
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseMetadata
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import org.slf4j.LoggerFactory
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.infrastructure.repo.AvbruttTilbakekrevingsbehandlingHendelsestype
import tilbakekreving.infrastructure.repo.IverksattTilbakekrevingsbehandlingHendelsestype
import java.time.Clock
import java.util.UUID

class LukkOppgaveForTilbakekrevingshendelserKonsument(
    private val sakService: SakService,
    private val oppgaveService: OppgaveService,
    private val tilbakekrevingsbehandlingHendelseRepo: TilbakekrevingsbehandlingRepo,
    private val oppgaveHendelseRepo: OppgaveHendelseRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : Hendelseskonsument {
    override val konsumentId = HendelseskonsumentId("LukkOppgaveForTilbakekrevingsbehandlingHendelser")

    private val log = LoggerFactory.getLogger(this::class.java)

    fun lukkOppgaver(correlationId: CorrelationId) {
        Either.catch {
            lukkOppgaverForAvbrutt(correlationId)
            lukkOppgaverForIverksatt(correlationId)
        }.mapLeft {
            log.error(
                "Kunne ikke lukke oppgave(r) for tilbakekrevingsbehandling: Det ble kastet en exception for konsument $konsumentId",
                it,
            )
        }
    }

    private fun lukkOppgaverForIverksatt(correlationId: CorrelationId) {
        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = IverksattTilbakekrevingsbehandlingHendelsestype,
        ).forEach { (sakId, hendelsesIder) ->
            prosesserSak(sakId, hendelsesIder, correlationId)
        }
    }

    private fun lukkOppgaverForAvbrutt(correlationId: CorrelationId) {
        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = AvbruttTilbakekrevingsbehandlingHendelsestype,
        ).forEach { (sakId, hendelsesIder) ->
            prosesserSak(sakId, hendelsesIder, correlationId)
        }
    }

    private fun prosesserSak(
        sakId: UUID,
        hendelsesIder: Nel<HendelseId>,
        correlationId: CorrelationId,
    ) {
        log.info("starter lukking av oppgaver for tilbakekrevingsbehandling-hendelser på sak $sakId")

        val sak = sakService.hentSak(sakId)
            .getOrElse { return Unit.also { log.error("Kunne ikke hente sak $sakId for hendelser $hendelsesIder for å lukke oppgave for tilbakekrevingsbehandling") } }

        hendelsesIder.mapOneIndexed { idx, relatertHendelsesId ->
            val relatertHendelse = tilbakekrevingsbehandlingHendelseRepo.hentHendelse(relatertHendelsesId)
                ?: return@mapOneIndexed Unit.also { log.error("Feil ved henting av hendelse for å lukke oppgave. sak $sakId, hendelse $relatertHendelsesId") }

            oppgaveHendelseRepo.hentHendelseForRelatert(relatertHendelse.hendelseId, sak.id)?.let {
                return@mapOneIndexed Unit.also {
                    hendelsekonsumenterRepo.lagre(relatertHendelse.hendelseId, konsumentId)
                    log.error("Feil ved oppretting av oppgave for tilbakekreving ${relatertHendelse.id.value}. Oppgave allerede opprettet for hendelse ${relatertHendelse.hendelseId}. Konsumenten vil lagre denne hendelsen")
                }
            }

            val alleSakensOppgaveHendelser = oppgaveHendelseRepo.hentForSak(sakId)

            val tilbakekrevingshendelsesSerie =
                tilbakekrevingsbehandlingHendelseRepo.hentBehandlingsSerieFor(relatertHendelse)

            val seriensOppgaveHendelser =
                alleSakensOppgaveHendelser.pickByCondition(tilbakekrevingshendelsesSerie.hendelsesIder()) { oppgaveHendelse, hendelseId ->
                    oppgaveHendelse.relaterteHendelser.contains(hendelseId)
                }

            return@mapOneIndexed seriensOppgaveHendelser.whenever(
                // logger som error, da det kan være greit å sjekke i dette de første par gagene hvis den skulle treffe
                { Unit.also { log.error("Kunne ikke lukke oppgave for $relatertHendelsesId, for sak ${relatertHendelse.sakId} fordi at det ikke finnes noen oppgave hendelser") } },
                { oppgaveHendelser ->
                    // verifiserer at det kun finnes 1 oppgaveId
                    Either.catch {
                        oppgaveHendelser.distinctBy { it.oppgaveId }.single().let {
                            oppgaveHendelser.maxByOrNull { it.versjon }!!
                        }
                    }.mapLeft { throwable ->
                        Unit.also {
                            log.error(
                                "Feil ved verifisering av at det fantes kun 1 oppgave Id for lukking av oppgave for sak ${sak.id} for hendelser $hendelsesIder",
                                throwable,
                            )
                        }
                    }.map {
                        opprettNyLukkOppgaveHendelse(
                            relaterteHendelse = relatertHendelse.hendelseId,
                            nesteVersjon = sak.versjon.inc(idx),
                            sakInfo = sak.info(),
                            correlationId = correlationId,
                            tidligereOppgaveHendelse = it,
                        ).map {
                            sessionFactory.withTransactionContext { context ->
                                oppgaveHendelseRepo.lagre(
                                    hendelse = it.first,
                                    meta = it.second,
                                    sessionContext = context,
                                )
                                hendelsekonsumenterRepo.lagre(relatertHendelse.hendelseId, konsumentId, context)
                            }
                        }.mapLeft {
                            log.error("Feil skjedde ved lukking av oppgave for tilbakekrevingsbehandling $it. For sak $sakId, hendelse ${relatertHendelse.id}")
                        }
                    }
                },
            )
        }
    }

    /**
     * Asynkron - oppretter oppgaver med systembruker.
     */
    private fun opprettNyLukkOppgaveHendelse(
        relaterteHendelse: HendelseId,
        nesteVersjon: Hendelsesversjon,
        tidligereOppgaveHendelse: OppgaveHendelse,
        sakInfo: SakInfo,
        correlationId: CorrelationId,
    ): Either<KunneIkkeLukkeOppgave, Pair<OppgaveHendelse, OppgaveHendelseMetadata>> {
        return oppgaveService.lukkOppgaveMedSystembruker(tidligereOppgaveHendelse.oppgaveId).fold(
            {
                when (it) {
                    is no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave.FeilVedHentingAvOppgave -> KunneIkkeLukkeOppgave.FeilVedLukkingAvOppgave.left()
                    is no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave.FeilVedHentingAvToken -> KunneIkkeLukkeOppgave.FeilVedLukkingAvOppgave.left()
                    is no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave.FeilVedOppdateringAvOppgave -> when (
                        val alleredeFerdigstilt = it.originalFeil
                    ) {
                        is KunneIkkeOppdatereOppgave.FeilVedHentingAvOppgave -> KunneIkkeLukkeOppgave.FeilVedLukkingAvOppgave.left()
                        is KunneIkkeOppdatereOppgave.FeilVedHentingAvToken -> KunneIkkeLukkeOppgave.FeilVedLukkingAvOppgave.left()
                        is KunneIkkeOppdatereOppgave.FeilVedRequest -> KunneIkkeLukkeOppgave.FeilVedLukkingAvOppgave.left()
                        is KunneIkkeOppdatereOppgave.OppgaveErFerdigstilt -> Pair(
                            toManueltLukketHendelse(
                                tidligereOppgaveHendelse = tidligereOppgaveHendelse,
                                nesteVersjon = nesteVersjon,
                                sakInfo = sakInfo,
                                relaterteHendelse = relaterteHendelse,
                                alleredeFerdigstilt = alleredeFerdigstilt,
                            ),
                            OppgaveHendelseMetadata(
                                correlationId = correlationId,
                                ident = null,
                                brukerroller = listOf(),
                                request = alleredeFerdigstilt.jsonRequest,
                                response = alleredeFerdigstilt.jsonResponse,
                            ),
                        ).right()
                    }
                }
            },
            {
                Pair(
                    OppgaveHendelse.Lukket.Maskinelt(
                        hendelseId = HendelseId.generer(),
                        hendelsestidspunkt = Tidspunkt.now(clock),
                        oppgaveId = tidligereOppgaveHendelse.oppgaveId,
                        versjon = nesteVersjon,
                        sakId = sakInfo.sakId,
                        relaterteHendelser = listOf(relaterteHendelse),
                        beskrivelse = it.beskrivelse,
                        tidligereHendelseId = tidligereOppgaveHendelse.hendelseId,
                    ),
                    OppgaveHendelseMetadata(
                        correlationId = correlationId,
                        ident = null,
                        brukerroller = listOf(),
                        request = it.request,
                        response = it.response,
                    ),
                ).right()
            },
        )
    }

    private fun toManueltLukketHendelse(
        tidligereOppgaveHendelse: OppgaveHendelse,
        nesteVersjon: Hendelsesversjon,
        sakInfo: SakInfo,
        relaterteHendelse: HendelseId,
        alleredeFerdigstilt: KunneIkkeOppdatereOppgave.OppgaveErFerdigstilt,
    ) = OppgaveHendelse.Lukket.Manuelt(
        hendelseId = HendelseId.generer(),
        hendelsestidspunkt = Tidspunkt.now(clock),
        oppgaveId = tidligereOppgaveHendelse.oppgaveId,
        versjon = nesteVersjon,
        sakId = sakInfo.sakId,
        relaterteHendelser = listOf(relaterteHendelse),
        tidligereHendelseId = tidligereOppgaveHendelse.hendelseId,
        ferdigstiltAv = alleredeFerdigstilt.ferdigstiltAv,
    )
}
