package tilbakekreving.application.service.consumer

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.DokumentMedMetadataUtenFil.Companion.tilDokumentUtenFil
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.BrevService
import dokument.domain.brev.Brevvalg
import dokument.domain.hendelser.DokumentHendelseRepo
import dokument.domain.hendelser.GenerertDokumentHendelse
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.extensions.mapOneIndexed
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import org.slf4j.LoggerFactory
import tilbakekreving.domain.IverksattHendelse
import tilbakekreving.domain.IverksattTilbakekrevingsbehandling
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.vedtaksbrev.VedtaksbrevTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.infrastructure.repo.IverksattTilbakekrevingsbehandlingHendelsestype
import java.time.Clock
import java.util.UUID

class GenererVedtaksbrevTilbakekrevingKonsument(
    private val sakService: SakService,
    private val brevService: BrevService,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : Hendelseskonsument {

    override val konsumentId = HendelseskonsumentId("GenererVedtaksbrevTilbakekrevingKonsument")
    private val log = LoggerFactory.getLogger(this::class.java)

    fun genererVedtaksbrev(correlationId: CorrelationId) {
        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = IverksattTilbakekrevingsbehandlingHendelsestype,
        ).forEach { (sakId, hendelsesIder) -> genererDokumenterForSak(sakId, hendelsesIder, correlationId) }
    }

    private fun genererDokumenterForSak(
        sakId: UUID,
        hendelsesIder: Nel<HendelseId>,
        correlationId: CorrelationId,
    ) {
        val sak = sakService.hentSak(sakId)
            .getOrElse {
                log.error("Feil under generering av vedtaksbrev for tilbakekreving: Kunne ikke hente sak $sakId")
                return
            }
        hendelsesIder.mapOneIndexed { index, hendelseId ->
            genererDokumentForSakOgHendelse(sak, hendelseId, correlationId, sak.versjon.inc(index))
        }
    }

    private fun genererDokumentForSakOgHendelse(
        sak: Sak,
        hendelseId: HendelseId,
        correlationId: CorrelationId,
        nesteVersjon: Hendelsesversjon,
    ) {
        tilbakekrevingsbehandlingRepo.hentForSak(sak.id).hentDokumenterForHendelseId(hendelseId).let {
            if (it != null) {
                return Unit.also {
                    hendelsekonsumenterRepo.lagre(hendelseId, konsumentId)
                    log.error("Feil under generering av vedtaksbrev for tilbakekreving: Fant dokumenter knyttet til hendelsen. For sak ${sak.id} og hendelse $hendelseId. Denne hendelsen blir lagret i konsumenten")
                }
            }
        }

        val iverksettHendelse =
            (tilbakekrevingsbehandlingRepo.hentHendelse(hendelseId) as? IverksattHendelse)
                ?: return Unit.also {
                    log.error("Feil under generering av vedtaksbrev for tilbakekreving: hendelsen var ikke av type IverksattHendelse for sak ${sak.id} og hendelse $hendelseId")
                }

        val behandling = sak.behandlinger.tilbakekrevinger.hent(iverksettHendelse.id)?.let {
            (it as? IverksattTilbakekrevingsbehandling) ?: return Unit.also {
                log.error("Feil under generering av vedtaksbrev for tilbakekreving: Fant ikke behandling ${iverksettHendelse.id}, for sak ${sak.id} og hendelse $hendelseId")
            }
        } ?: return Unit.also {
            log.error("Feil under generering av vedtaksbrev for tilbakekreving: Behandling ${iverksettHendelse.id} er ikke i IverksattTilbakekrevingsbehandling tilstand, for sak ${sak.id} og hendelse $hendelseId")
        }

        return when (behandling.vedtaksbrevvalg) {
            is Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev -> Unit.also {
                log.info("Behandling ${behandling.id} er markert som ikke å sende brev for, for sak ${sak.id} og hendelse $hendelseId")
                hendelsekonsumenterRepo.lagre(hendelseId, konsumentId)
            }

            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.UtenFritekst -> throw IllegalStateException("Tilbakekrevingsbehandling ${behandling.id} har brevvalg for VedtaksbrevUtenFritekst. Det skal bare være mulig å ikke sende brev, eller VedtaksbrevMedFritekst")
            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst -> throw IllegalStateException("Tilbakekrevingsbehandling ${behandling.id} har brevvalg for InformasjonsbrevMedFritekst. Det skal bare være mulig å ikke sende brev, eller VedtaksbrevMedFritekst")
            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst -> genererVedtaksbrev(
                iverksattHendelse = iverksettHendelse,
                behandling = behandling,
                nesteVersjon = nesteVersjon,
                sakInfo = sak.info(),
                correlationId = correlationId,
            ).fold(
                {
                    log.error("Feil under generering av vedtaksbrev for tilbakekreving: Kunne ikke lage dokument for sak ${sak.id}, hendelse $hendelseId og behandling ${behandling.id}. Underliggende feil: $it")
                },
                {
                    sessionFactory.withTransactionContext { context ->
                        dokumentHendelseRepo.lagreGenerertDokumentHendelse(
                            hendelse = it.first,
                            hendelseFil = it.second,
                            meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
                            sessionContext = context,
                        )
                        hendelsekonsumenterRepo.lagre(iverksettHendelse.hendelseId, konsumentId, context)
                    }
                },
            )
        }
    }

    private fun genererVedtaksbrev(
        iverksattHendelse: IverksattHendelse,
        behandling: IverksattTilbakekrevingsbehandling,
        nesteVersjon: Hendelsesversjon,
        sakInfo: SakInfo,
        correlationId: CorrelationId,
    ): Either<KunneIkkeLageDokument, Pair<GenerertDokumentHendelse, HendelseFil>> {
        val command = VedtaksbrevTilbakekrevingsbehandlingDokumentCommand(
            fødselsnummer = sakInfo.fnr,
            saksnummer = sakInfo.saksnummer,
            correlationId = correlationId,
            sakId = sakInfo.sakId,
            saksbehandler = behandling.forrigeSteg.sendtTilAttesteringAv,
            attestant = iverksattHendelse.utførtAv,
            fritekst = (behandling.vedtaksbrevvalg as Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst).fritekst,
            vurderingerMedKrav = behandling.vurderingerMedKrav,
        )

        val dokument = brevService.lagDokument(command = command)
            .getOrElse { return it.left() }
            .leggTilMetadata(
                Dokument.Metadata(
                    sakId = sakInfo.sakId,
                    vedtakId = iverksattHendelse.vedtakId,
                    tilbakekrevingsbehandlingId = iverksattHendelse.id.value,
                ),
            )

        val dokumentHendelse = GenerertDokumentHendelse(
            hendelseId = HendelseId.generer(),
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = nesteVersjon,
            sakId = sakInfo.sakId,
            relatertHendelse = iverksattHendelse.hendelseId,
            dokumentUtenFil = dokument.tilDokumentUtenFil(),
            skalSendeBrev = true,
        )

        val hendelseFil = HendelseFil(hendelseId = dokumentHendelse.hendelseId, fil = dokument.generertDokument)

        return (dokumentHendelse to hendelseFil).right()
    }
}
