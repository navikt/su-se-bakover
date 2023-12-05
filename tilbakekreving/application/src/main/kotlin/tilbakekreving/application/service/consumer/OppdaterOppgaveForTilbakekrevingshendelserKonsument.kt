package tilbakekreving.application.service.consumer

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.extensions.mapOneIndexed
import no.nav.su.se.bakover.common.extensions.pickByCondition
import no.nav.su.se.bakover.common.extensions.whenever
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseMetadata
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import org.slf4j.LoggerFactory
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.infrastructure.repo.ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype
import tilbakekreving.infrastructure.repo.TilbakekrevingsbehandlingTilAttesteringHendelsestype
import tilbakekreving.infrastructure.repo.UnderkjentTilbakekrevingsbehandlingHendelsestype
import java.time.Clock
import java.util.UUID

class OppdaterOppgaveForTilbakekrevingshendelserKonsument(
    private val sakService: SakService,
    private val oppgaveService: OppgaveService,
    private val tilbakekrevingsbehandlingHendelseRepo: TilbakekrevingsbehandlingRepo,
    private val oppgaveHendelseRepo: OppgaveHendelseRepo,
    private val hendelseRepo: HendelseRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : Hendelseskonsument {
    override val konsumentId = HendelseskonsumentId("OppdaterOppgaveForTilbakekrevingsbehandlingHendelser")

    private val log = LoggerFactory.getLogger(this::class.java)

    fun oppdaterOppgaver(correlationId: CorrelationId) {
        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype,
        ).forEach { (sakId, hendelsesIder) ->
            prosesserSak(
                sakId,
                hendelsesIder,
                correlationId,
                OppdaterOppgaveInfo(beskrivelse = "Forhåndsvarsel er opprettet"),
            )
        }
        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = TilbakekrevingsbehandlingTilAttesteringHendelsestype,
        ).forEach { (sakId, hendelsesIder) ->
            prosesserSak(
                sakId,
                hendelsesIder,
                correlationId,
                OppdaterOppgaveInfo(
                    beskrivelse = "Behandlingen er sendt til attestering",
                    oppgavetype = Oppgavetype.ATTESTERING,
                ),
            )
        }
        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = UnderkjentTilbakekrevingsbehandlingHendelsestype,
        ).forEach { (sakId, hendelsesIder) ->
            prosesserSak(
                sakId,
                hendelsesIder,
                correlationId,
                OppdaterOppgaveInfo(
                    beskrivelse = "Behandlingen er sendt tilbake for vurdering",
                    oppgavetype = Oppgavetype.BEHANDLE_SAK,
                ),
            )
        }
    }

    private fun prosesserSak(
        sakId: UUID,
        hendelsesIder: Nel<HendelseId>,
        correlationId: CorrelationId,
        oppdaterOppgaveInfo: OppdaterOppgaveInfo,
    ) {
        log.info("starter oppdatering av oppgaver for tilbakekrevingsbehandling-hendelser på sak $sakId")

        val sak = sakService.hentSak(sakId)
            .getOrElse { return Unit.also { log.error("Kunne ikke hente sak $sakId for hendelser $hendelsesIder for å oppdatere oppgave for tilbakekrevingsbehandling") } }

        hendelsesIder.mapOneIndexed { idx, relatertHendelsesId ->
            val relatertHendelse = tilbakekrevingsbehandlingHendelseRepo.hentHendelse(relatertHendelsesId)
                ?: return@mapOneIndexed Unit.also { log.error("Feil ved henting av hendelse for å oppdatere oppgave. sak $sakId, hendelse $relatertHendelsesId") }

            oppgaveHendelseRepo.hentHendelseForRelatert(relatertHendelse.hendelseId, sak.id)?.let {
                return@mapOneIndexed Unit.also {
                    hendelsekonsumenterRepo.lagre(relatertHendelse.hendelseId, konsumentId)
                    log.error("Feil ved oppdatering av oppgave for tilbakekreving ${relatertHendelse.id.value}. Oppgave allerede oppdatert for hendelse ${relatertHendelse.hendelseId}. Konsumenten vil lagre denne hendelsen")
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
                { Unit.also { log.info("Kunne ikke oppdatering oppgave for $relatertHendelsesId, for sak ${relatertHendelse.sakId} fordi at det ikke finnes noen oppgave hendelser") } },
                { oppgaveHendelser ->
                    // verifiserer at det kun finnes 1 oppgaveId
                    Either.catch {
                        oppgaveHendelser.distinctBy { it.oppgaveId }.single().let {
                            oppgaveHendelser.maxByOrNull { it.versjon }!!
                        }
                    }.mapLeft {
                        log.error("Feil ved verifisering av at det fantes kun 1 oppgave Id for oppdatering av oppgave for sak ${sak.id} for hendelser $hendelsesIder")
                    }.map {
                        oppdaterOppgave(
                            relaterteHendelse = relatertHendelse.hendelseId,
                            nesteVersjon = sak.versjon.inc(idx),
                            sakInfo = sak.info(),
                            correlationId = correlationId,
                            tidligereOppgaveHendelse = it,
                            oppdaterOppgaveInfo = oppdaterOppgaveInfo,
                        ).map {
                            sessionFactory.withTransactionContext { context ->
                                oppgaveHendelseRepo.lagre(it, context)
                                hendelsekonsumenterRepo.lagre(relatertHendelse.hendelseId, konsumentId, context)
                            }
                        }.mapLeft {
                            log.error(
                                "Feil skjedde ved oppdatering av oppgave for tilbakekrevingsbehandling $it. For sak $sakId, hendelse ${relatertHendelse.id}",
                                it,
                            )
                        }
                    }
                },
            )
        }
    }

    private fun oppdaterOppgave(
        relaterteHendelse: HendelseId,
        nesteVersjon: Hendelsesversjon,
        tidligereOppgaveHendelse: OppgaveHendelse,
        sakInfo: SakInfo,
        correlationId: CorrelationId,
        oppdaterOppgaveInfo: OppdaterOppgaveInfo,
    ): Either<KunneIkkeOppdatereOppgave, OppgaveHendelse> {
        return oppgaveService.oppdaterOppgave(
            oppgaveId = tidligereOppgaveHendelse.oppgaveId,
            oppdaterOppgaveInfo = oppdaterOppgaveInfo,
        )
            .mapLeft {
                // TODO - her må returnere lukket manuelt dersom den er blitt ferdigstilt
                KunneIkkeOppdatereOppgave.FeilVedLukkingAvOppgave
            }
            .map {
                OppgaveHendelse.Oppdatert(
                    hendelsestidspunkt = Tidspunkt.now(clock),
                    oppgaveId = tidligereOppgaveHendelse.oppgaveId,
                    versjon = nesteVersjon,
                    sakId = sakInfo.sakId,
                    relaterteHendelser = listOf(relaterteHendelse),
                    meta = OppgaveHendelseMetadata(
                        correlationId = correlationId,
                        ident = null,
                        brukerroller = listOf(),
                        request = it.request,
                        response = it.response,
                    ),
                    tidligereHendelseId = tidligereOppgaveHendelse.hendelseId,
                    beskrivelse = it.beskrivelse,
                    oppgavetype = it.oppgavetype,
                )
            }
    }
}
