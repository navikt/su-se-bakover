package tilbakekreving.application.service.kravgrunnlag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import org.slf4j.LoggerFactory
import tilbakekreving.domain.AvbruttTilbakekrevingsbehandling
import tilbakekreving.domain.KanAnnullere
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.kravgrunnlag.AnnullerKravgrunnlagCommand
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlagstatus
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagStatusendringPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.repo.AnnullerKravgrunnlagStatusEndringMeta
import tilbakekreving.domain.kravgrunnlag.repo.KravgrunnlagRepo
import tilbakekreving.domain.vedtak.KunneIkkeAnnullerePåbegynteVedtak
import tilbakekreving.domain.vedtak.Tilbakekrevingsklient
import tilgangstyring.application.TilgangstyringService
import tilgangstyring.domain.IkkeTilgangTilSak
import java.time.Clock

class AnnullerKravgrunnlagService(
    private val tilgangstyring: TilgangstyringService,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val sakService: SakService,
    private val kravgrunnlagRepo: KravgrunnlagRepo,
    private val tilbakekrevingsklient: Tilbakekrevingsklient,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun annuller(command: AnnullerKravgrunnlagCommand): Either<KunneIkkeAnnullereKravgrunnlag, Pair<Kravgrunnlag, AvbruttTilbakekrevingsbehandling?>> {
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
            ?: return KunneIkkeAnnullereKravgrunnlag.SakenHarIkkeKravgrunnlagSomKanAnnulleres.left()

        val kravgrunnlagOgBehandling =
            tilbakekrevingsbehandlingHendelser.hentKravgrunnlagOgBehandlingFor(command.kravgrunnlagHendelseId)
                ?: return KunneIkkeAnnullereKravgrunnlag.FantIkkeKravgrunnlag.left()

        if (uteståendeKravgrunnlagPåSak.hendelseId != kravgrunnlagOgBehandling.first.hendelseId) {
            return KunneIkkeAnnullereKravgrunnlag.InnsendtHendelseIdErIkkeDenSistePåSaken.left()
        }

        val avbruttHendelseOgBehandling = kravgrunnlagOgBehandling.second.let {
            if (it != null) {
                (kravgrunnlagOgBehandling.second as? KanAnnullere)?.annuller(
                    annulleringstidspunkt = Tidspunkt.now(clock),
                    annullertAv = command.annullertAv,
                    versjon = command.klientensSisteSaksversjon.inc(),
                ) ?: return KunneIkkeAnnullereKravgrunnlag.BehandlingenErIFeilTilstandForÅAnnullere.left()
            } else {
                null
            }
        }

        return tilbakekrevingsklient.annullerKravgrunnlag(command.annullertAv, kravgrunnlagOgBehandling.first).fold(
            ifLeft = {
                KunneIkkeAnnullereKravgrunnlag.FeilMotTilbakekrevingskomponenten(it).left()
            },
            ifRight = { råTilbakekrevingsvedtakForsendelse ->
                sessionFactory.withTransactionContext {
                    kravgrunnlagRepo.lagreKravgrunnlagPåSakHendelse(
                        KravgrunnlagStatusendringPåSakHendelse(
                            hendelseId = HendelseId.generer(),
                            versjon = command.klientensSisteSaksversjon.inc(2),
                            sakId = command.sakId,
                            hendelsestidspunkt = Tidspunkt.now(clock),
                            tidligereHendelseId = uteståendeKravgrunnlagPåSak.hendelseId,
                            saksnummer = sak.saksnummer,
                            eksternVedtakId = uteståendeKravgrunnlagPåSak.eksternVedtakId,
                            status = Kravgrunnlagstatus.Annullert,
                            eksternTidspunkt = uteståendeKravgrunnlagPåSak.eksternTidspunkt,
                        ),
                        AnnullerKravgrunnlagStatusEndringMeta(
                            correlationId = command.correlationId,
                            ident = command.utførtAv,
                            brukerroller = command.brukerroller,
                            tilbakekrevingsvedtakForsendelse = råTilbakekrevingsvedtakForsendelse,
                        ),
                        it,
                    )
                    if (avbruttHendelseOgBehandling != null) {
                        tilbakekrevingsbehandlingRepo.lagre(
                            hendelse = avbruttHendelseOgBehandling.first,
                            meta = command.toDefaultHendelsesMetadata(),
                            sessionContext = it,
                        )
                    }
                }

                val nyeHendelser = tilbakekrevingsbehandlingRepo.hentForSak(command.sakId)
                val sisteKravgrunnlag = nyeHendelser.kravgrunnlagPåSak.hentSisteKravgrunnlag()
                    ?: throw IllegalStateException("Fant ikke siste kravgrunnlag etter annullering for sak ${sak.id}, nummer ${sak.saksnummer}")

                Pair(sisteKravgrunnlag, avbruttHendelseOgBehandling?.second).right()
            },
        )
    }
}

sealed interface KunneIkkeAnnullereKravgrunnlag {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeAnnullereKravgrunnlag
    data object InnsendtHendelseIdErIkkeDenSistePåSaken : KunneIkkeAnnullereKravgrunnlag
    data object SakenHarIkkeKravgrunnlagSomKanAnnulleres : KunneIkkeAnnullereKravgrunnlag
    data object FantIkkeKravgrunnlag : KunneIkkeAnnullereKravgrunnlag
    data object BehandlingenErIFeilTilstandForÅAnnullere : KunneIkkeAnnullereKravgrunnlag
    data class FeilMotTilbakekrevingskomponenten(val underliggende: KunneIkkeAnnullerePåbegynteVedtak) :
        KunneIkkeAnnullereKravgrunnlag
}
