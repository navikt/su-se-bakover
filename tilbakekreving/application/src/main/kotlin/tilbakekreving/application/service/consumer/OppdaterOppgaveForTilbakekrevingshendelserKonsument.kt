package tilbakekreving.application.service.consumer

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.domain.extensions.mapOneIndexed
import no.nav.su.se.bakover.common.domain.extensions.pickByCondition
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.whenever
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseMetadata
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import org.slf4j.LoggerFactory
import tilbakekreving.domain.AvbruttHendelse
import tilbakekreving.domain.BrevTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.ForhåndsvarsletTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.IverksattHendelse
import tilbakekreving.domain.NotatTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.OppdatertKravgrunnlagPåTilbakekrevingHendelse
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingUtenKravgrunnlagHendelse
import tilbakekreving.domain.TilAttesteringHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.UnderkjentHendelse
import tilbakekreving.domain.VurdertTilbakekrevingsbehandlingHendelse
import tilbakekreving.infrastructure.repo.ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype
import tilbakekreving.infrastructure.repo.TilbakekrevingsbehandlingTilAttesteringHendelsestype
import tilbakekreving.infrastructure.repo.UnderkjentTilbakekrevingsbehandlingHendelsestype
import java.time.Clock
import java.util.UUID
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave as KunneIkkeOppdatereOppgaveDomain

class OppdaterOppgaveForTilbakekrevingshendelserKonsument(
    private val sakService: SakService,
    private val oppgaveService: OppgaveService,
    private val tilbakekrevingsbehandlingHendelseRepo: TilbakekrevingsbehandlingRepo,
    private val oppgaveHendelseRepo: OppgaveHendelseRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : Hendelseskonsument {
    override val konsumentId = HendelseskonsumentId("OppdaterOppgaveForTilbakekrevingsbehandlingHendelser")

    private val log = LoggerFactory.getLogger(this::class.java)

    fun oppdaterOppgaver(correlationId: CorrelationId) {
        Either.catch {
            oppdaterOppgaveEtterForhåndsvarsel(correlationId)
            oppdaterOppgaveEtterSendtTilAttestering(correlationId)
            oppdaterOppgaveEtterUnderkjennelse(correlationId)
        }.mapLeft {
            log.error(
                "Kunne ikke oppdatere oppgave(r) for tilbakekrevingsbehandling: Det ble kastet en exception for konsument $konsumentId",
                it,
            )
        }
    }

    private fun oppdaterOppgaveEtterUnderkjennelse(correlationId: CorrelationId) {
        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = UnderkjentTilbakekrevingsbehandlingHendelsestype,
        ).forEach { (sakId, hendelsesIder) ->
            prosesserSak(
                sakId,
                hendelsesIder,
                correlationId,
            )
        }
    }

    private fun oppdaterOppgaveEtterSendtTilAttestering(correlationId: CorrelationId) {
        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = TilbakekrevingsbehandlingTilAttesteringHendelsestype,
        ).forEach { (sakId, hendelsesIder) ->
            prosesserSak(
                sakId,
                hendelsesIder,
                correlationId,
            )
        }
    }

    private fun oppdaterOppgaveEtterForhåndsvarsel(correlationId: CorrelationId) {
        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype,
        ).forEach { (sakId, hendelsesIder) ->
            prosesserSak(
                sakId,
                hendelsesIder,
                correlationId,
            )
        }
    }

    private fun prosesserSak(
        sakId: UUID,
        tilbakekrevingsbehandlingHendelseIder: Nel<HendelseId>,
        correlationId: CorrelationId,
    ) {
        log.info("starter oppdatering av oppgaver for tilbakekrevingsbehandling-hendelser på sak $sakId")

        val sak = sakService.hentSak(sakId)
            .getOrElse { return Unit.also { log.error("Kunne ikke hente sak $sakId for hendelser $tilbakekrevingsbehandlingHendelseIder for å oppdatere oppgave for tilbakekrevingsbehandling") } }

        tilbakekrevingsbehandlingHendelseIder.mapOneIndexed { idx, tilbakekrevingsbehandlingHendelseId ->
            val tilbakekrevingsbehandlingHendelse =
                tilbakekrevingsbehandlingHendelseRepo.hentHendelse(tilbakekrevingsbehandlingHendelseId)
                    ?: return@mapOneIndexed Unit.also { log.error("Feil ved henting av hendelse for å oppdatere oppgave. sak $sakId, hendelse $tilbakekrevingsbehandlingHendelseId") }

            val alleSakensOppgaveHendelser = oppgaveHendelseRepo.hentForSak(sakId)

            if (alleSakensOppgaveHendelser.any { it.relaterteHendelser.contains(tilbakekrevingsbehandlingHendelse.hendelseId) }) {
                return@mapOneIndexed Unit.also {
                    hendelsekonsumenterRepo.lagre(tilbakekrevingsbehandlingHendelse.hendelseId, konsumentId)
                    log.error("Oppgave allerede oppdatert for hendelse ${tilbakekrevingsbehandlingHendelse.hendelseId}. Vil ikke prøve denne på nytt.")
                }
            }

            val tilbakekrevingshendelsesSerie =
                tilbakekrevingsbehandlingHendelseRepo.hentBehandlingsSerieFor(
                    sakId = sakId,
                    tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingHendelse.id,
                )
            val seriensOppgaveHendelser =
                alleSakensOppgaveHendelser.pickByCondition(tilbakekrevingshendelsesSerie.hendelsesIder()) { oppgaveHendelse, hendelseId ->
                    oppgaveHendelse.relaterteHendelser.contains(hendelseId)
                }
            val oppdaterOppgaveInfo: OppdaterOppgaveInfo = when (tilbakekrevingsbehandlingHendelse) {
                is ForhåndsvarsletTilbakekrevingsbehandlingHendelse -> OppdaterOppgaveInfo(
                    beskrivelse = "Forhåndsvarsel er opprettet",
                    tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(tilbakekrevingsbehandlingHendelse.utførtAv.toString()),
                )

                is TilAttesteringHendelse -> OppdaterOppgaveInfo(
                    beskrivelse = "Behandlingen er sendt til attestering",
                    oppgavetype = Oppgavetype.ATTESTERING,
                    // Når vi sender en tilbakekrevingsbehandling til attestering, skal vi enten a) ikke tilordne ressurs, eller b) sette den til attestanten som underkjente den
                    tilordnetRessurs = tilbakekrevingshendelsesSerie.hentSisteUnderkjentHendelse()?.let {
                        OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(it.utførtAv.toString())
                    } ?: OppdaterOppgaveInfo.TilordnetRessurs.IkkeTilordneRessurs,
                )

                is UnderkjentHendelse -> OppdaterOppgaveInfo(
                    beskrivelse = "Behandlingen er sendt tilbake for vurdering",
                    oppgavetype = Oppgavetype.BEHANDLE_SAK,
                    // Når vi underkjenner en tilbakekrevingsbehandling, så vil vi sende den tilbake til saksbehandler som sendte den til attestering
                    tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(
                        tilbakekrevingshendelsesSerie.hentSisteSendtTilAttesteringHendelse()!!.utførtAv.toString(),
                    ),
                )

                is AvbruttHendelse,
                is OpprettetTilbakekrevingsbehandlingHendelse,
                is OpprettetTilbakekrevingsbehandlingUtenKravgrunnlagHendelse,
                is VurdertTilbakekrevingsbehandlingHendelse,
                is BrevTilbakekrevingsbehandlingHendelse,
                is IverksattHendelse,
                is OppdatertKravgrunnlagPåTilbakekrevingHendelse,
                is NotatTilbakekrevingsbehandlingHendelse,
                -> throw IllegalStateException("Oppdater oppgave under tilbakekrevingsbehandling: Uforventet hendelse ${tilbakekrevingsbehandlingHendelse::class.simpleName}. Sak: $sakId, tilbakekrevingsbehandlinghendelseId: ${tilbakekrevingsbehandlingHendelse.id}")
            }

            return@mapOneIndexed seriensOppgaveHendelser.whenever(
                // logger som error, da det kan være greit å sjekke i dette de første par gagene hvis den skulle treffe
                { Unit.also { log.info("Kunne ikke oppdatering oppgave for $tilbakekrevingsbehandlingHendelseId, for sak ${tilbakekrevingsbehandlingHendelse.sakId} fordi at det ikke finnes noen oppgave hendelser") } },
                { oppgaveHendelser ->
                    // verifiserer at det kun finnes 1 oppgaveId
                    Either.catch {
                        oppgaveHendelser.distinctBy { it.oppgaveId }.single().let {
                            oppgaveHendelser.maxByOrNull { it.versjon }!!
                        }
                    }.mapLeft {
                        log.error("Feil ved verifisering av at det fantes kun 1 oppgave Id for oppdatering av oppgave for sak ${sak.id} for hendelser $tilbakekrevingsbehandlingHendelseIder")
                    }.map {
                        oppdaterOppgave(
                            relaterteHendelse = tilbakekrevingsbehandlingHendelse.hendelseId,
                            nesteVersjon = sak.versjon.inc(idx),
                            sakInfo = sak.info(),
                            correlationId = correlationId,
                            tidligereOppgaveHendelse = it,
                            oppdaterOppgaveInfo = oppdaterOppgaveInfo,
                        ).map {
                            sessionFactory.withTransactionContext { context ->
                                oppgaveHendelseRepo.lagre(it.first, it.second, context)
                                hendelsekonsumenterRepo.lagre(
                                    tilbakekrevingsbehandlingHendelse.hendelseId,
                                    konsumentId,
                                    context,
                                )
                            }
                        }.mapLeft {
                            log.error(
                                "Feil skjedde ved oppdatering av oppgave for tilbakekrevingsbehandling $it. For sak $sakId, hendelse ${tilbakekrevingsbehandlingHendelse.id}, se sikkerlogg for mer info",
                                RuntimeException("Genererer stacktrace for enklere debug"),
                            )
                            sikkerLogg.error(
                                "Feil skjedde ved oppdatering av oppgave for tilbakekrevingsbehandling $it. For sak $sakId, hendelse ${tilbakekrevingsbehandlingHendelse.id}. Underliggende feil: $it",
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
    ): Either<KunneIkkeOppdatereOppgave, Pair<OppgaveHendelse, OppgaveHendelseMetadata>> {
        return oppgaveService.oppdaterOppgaveMedSystembruker(
            oppgaveId = tidligereOppgaveHendelse.oppgaveId,
            oppdaterOppgaveInfo = oppdaterOppgaveInfo,
        ).fold(
            {
                when (it) {
                    KunneIkkeOppdatereOppgaveDomain.FeilVedHentingAvOppgave,
                    KunneIkkeOppdatereOppgaveDomain.FeilVedHentingAvToken,
                    KunneIkkeOppdatereOppgaveDomain.FeilVedRequest,
                    -> KunneIkkeOppdatereOppgave.FeilVedLukkingAvOppgave(it).left()

                    is KunneIkkeOppdatereOppgaveDomain.OppgaveErFerdigstilt -> Pair(
                        OppgaveHendelse.Lukket.Manuelt(
                            hendelsestidspunkt = Tidspunkt.now(clock),
                            oppgaveId = tidligereOppgaveHendelse.oppgaveId,
                            versjon = nesteVersjon,
                            sakId = sakInfo.sakId,
                            relaterteHendelser = listOf(relaterteHendelse),
                            tidligereHendelseId = tidligereOppgaveHendelse.hendelseId,
                            ferdigstiltAv = it.ferdigstiltAv,
                        ),
                        OppgaveHendelseMetadata(
                            correlationId = correlationId,
                            ident = null,
                            brukerroller = listOf(),
                            request = it.jsonRequest,
                            response = it.jsonResponse,
                        ),
                    ).right()
                }
            },
            {
                Pair(
                    OppgaveHendelse.Oppdatert(
                        hendelsestidspunkt = Tidspunkt.now(clock),
                        oppgaveId = tidligereOppgaveHendelse.oppgaveId,
                        versjon = nesteVersjon,
                        sakId = sakInfo.sakId,
                        relaterteHendelser = listOf(relaterteHendelse),
                        tidligereHendelseId = tidligereOppgaveHendelse.hendelseId,
                        beskrivelse = it.beskrivelse,
                        oppgavetype = it.oppgavetype,
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
}
