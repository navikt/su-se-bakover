package tilbakekreving.application.service.kravgrunnlag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.statistikk.toTilbakeStatistikkAnnuller
import tilbakekreving.domain.AvbruttTilbakekrevingsbehandling
import tilbakekreving.domain.KanAnnullere
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.kravgrunnlag.AnnullerKravgrunnlagCommand
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlagstatus
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagStatusendringPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.repo.AnnullerKravgrunnlagStatusEndringMeta
import tilbakekreving.domain.kravgrunnlag.repo.KravgrunnlagRepo
import tilbakekreving.domain.vedtak.Tilbakekrevingsklient
import tilgangstyring.application.TilgangstyringService
import java.time.Clock

class AnnullerKravgrunnlagService(
    private val tilgangstyring: TilgangstyringService,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val sakService: SakService,
    private val kravgrunnlagRepo: KravgrunnlagRepo,
    private val tilbakekrevingsklient: Tilbakekrevingsklient,
    private val sessionFactory: SessionFactory,
    private val sakStatistikkRepo: SakStatistikkRepo,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun annuller(command: AnnullerKravgrunnlagCommand): Either<KunneIkkeAnnullereKravgrunnlag, AvbruttTilbakekrevingsbehandling?> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeAnnullereKravgrunnlag.IkkeTilgang(it).left()
        }
        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalStateException("Kunne ikke oppdatere kravgrunnlag for tilbakekrevingsbehandling, fant ikke sak. Command: $command")
        }
        if (sak.versjon != command.klientensSisteSaksversjon) {
            log.info("Oppdater kravgrunnlag - Sakens versjon (${sak.versjon}) er ulik saksbehandlers versjon. Command: $command")
        }
        val tilbakekrevingsbehandlingHendelser = tilbakekrevingsbehandlingRepo.hentForSak(command.sakId)
        val uteståendeKravgrunnlagPåSak = tilbakekrevingsbehandlingHendelser.hentUteståendeKravgrunnlag()
            ?: return KunneIkkeAnnullereKravgrunnlag.SakenHarIkkeKravgrunnlagSomKanAnnulleres.left().also {
                log.error("Prøvde å annullere et kravgrunnlag på en sak ${sak.saksnummer} som ikke har et utestående kravgrunnlag. Se sikkerlogg for mer informasjon.")
                sikkerLogg.error("Prøvde å annullere et kravgrunnlag på en sak ${sak.saksnummer} som ikke har et utestående kravgrunnlag. Command: $command")
            }
        val kravgrunnlag = tilbakekrevingsbehandlingHendelser.hentKravrunnlag(command.kravgrunnlagHendelseId)
            ?: return KunneIkkeAnnullereKravgrunnlag.FantIkkeKravgrunnlag.left().also {
                log.error("Prøvde å annullere et kravgrunnlag som ikke finnes på sak ${sak.saksnummer}. Se sikkerlogg for mer informasjon.")
                sikkerLogg.error("Prøvde å annullere et kravgrunnlag som ikke finnes på sak ${sak.saksnummer}. Command: $command")
            }

        if (uteståendeKravgrunnlagPåSak.hendelseId != kravgrunnlag.hendelseId) {
            return KunneIkkeAnnullereKravgrunnlag.InnsendtHendelseIdErIkkeDenSistePåSaken.left().also {
                log.error("Prøvde å annullere et kravgrunnlag som ikke er det siste på saken ${sak.saksnummer}. Se sikkerlogg for mer informasjon.")
                sikkerLogg.error("Prøvde å annullere et kravgrunnlag som ikke er det siste på saken ${sak.saksnummer}. Command: $command")
            }
        }

        val behandling =
            tilbakekrevingsbehandlingHendelser.hentBehandlingForKravgrunnlag(uteståendeKravgrunnlagPåSak.hendelseId)

        val (avbruttHendelse, avbruttBehandling) = behandling?.let { tilbakekrevingsbehandling ->
            (tilbakekrevingsbehandling as? KanAnnullere)?.annuller(
                annulleringstidspunkt = Tidspunkt.now(clock),
                annullertAv = command.annullertAv,
                versjon = command.klientensSisteSaksversjon.inc(),
            ) ?: return KunneIkkeAnnullereKravgrunnlag.BehandlingenErIFeilTilstandForÅAnnullere.left().also {
                log.error("Prøvde å annullere en tilbakekrevingsbehandling ${tilbakekrevingsbehandling.id} som ikke kan annulleres. Se sikkerlogg for mer informasjon.")
                sikkerLogg.error("Prøvde å annullere en tilbakekrevingsbehandling ${tilbakekrevingsbehandling.id} som ikke kan annulleres. Command: $command")
            }
        } ?: (null to null)

        return tilbakekrevingsklient.annullerKravgrunnlag(command.annullertAv, kravgrunnlag).mapLeft {
            KunneIkkeAnnullereKravgrunnlag.FeilMotTilbakekrevingskomponenten(it)
        }.map { råTilbakekrevingsvedtakForsendelse ->
            sessionFactory.withTransactionContext { tx ->
                kravgrunnlagRepo.lagreKravgrunnlagPåSakHendelse(
                    KravgrunnlagStatusendringPåSakHendelse(
                        hendelseId = HendelseId.generer(),
                        versjon = if (avbruttHendelse == null) {
                            command.klientensSisteSaksversjon.inc()
                        } else {
                            command.klientensSisteSaksversjon.inc(
                                2,
                            )
                        },
                        sakId = sak.id,
                        hendelsestidspunkt = Tidspunkt.now(clock),
                        tidligereHendelseId = uteståendeKravgrunnlagPåSak.hendelseId,
                        saksnummer = sak.saksnummer,
                        eksternVedtakId = uteståendeKravgrunnlagPåSak.eksternVedtakId,
                        status = Kravgrunnlagstatus.Annullert,
                        eksternTidspunkt = råTilbakekrevingsvedtakForsendelse.tidspunkt,
                    ),
                    AnnullerKravgrunnlagStatusEndringMeta(
                        correlationId = command.correlationId,
                        ident = command.annullertAv,
                        brukerroller = command.brukerroller,
                        tilbakekrevingsvedtakForsendelse = råTilbakekrevingsvedtakForsendelse,
                    ),
                    tx,
                )
                if (avbruttHendelse != null) {
                    tilbakekrevingsbehandlingRepo.lagre(
                        hendelse = avbruttHendelse,
                        meta = command.toDefaultHendelsesMetadata(),
                        sessionContext = tx,
                    )
                }
                avbruttBehandling?.let {
                    sakStatistikkRepo.lagreSakStatistikk(it.toTilbakeStatistikkAnnuller(clock), tx)
                }
            }
            avbruttBehandling
        }
    }
}
