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
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import org.slf4j.LoggerFactory
import tilbakekreving.domain.AvbruttHendelse
import tilbakekreving.domain.AvbruttTilbakekrevingsbehandling
import tilbakekreving.domain.ForhåndsvarsleTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.avbrutt.AvbruttTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.infrastructure.repo.AvbruttTilbakekrevingsbehandlingHendelsestype
import java.time.Clock
import java.util.UUID

/**
 * @see ForhåndsvarsleTilbakekrevingsbehandlingHendelse hendelsen som trigget konsumenten.
 */
class GenererDokumentForAvbruttTilbakekrevingsbehandlingKonsument(
    private val sakService: SakService,
    private val brevService: BrevService,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : Hendelseskonsument {

    override val konsumentId = HendelseskonsumentId("GenererDokumentForAvbruttTilbakekrevingsbehandlingKonsument")
    private val log = LoggerFactory.getLogger(this::class.java)

    fun genererDokumenter(correlationId: CorrelationId) {
        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = AvbruttTilbakekrevingsbehandlingHendelsestype,
        ).forEach { (sakId, hendelsesIder) -> genererDokumenterForSak(sakId, hendelsesIder, correlationId) }
    }

    private fun genererDokumenterForSak(
        sakId: UUID,
        hendelsesIder: Nel<HendelseId>,
        correlationId: CorrelationId,
    ) {
        val sak = sakService.hentSak(sakId)
            .getOrElse {
                log.error("Feil under generering av avbrutt-dokument: Kunne ikke hente sak $sakId")
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
        val sakId = sak.id
        val avsluttHendelse =
            (tilbakekrevingsbehandlingRepo.hentHendelse(hendelseId) as? AvbruttHendelse)
                ?: return Unit.also {
                    log.error("Feil under generering av avbruttdokument: hendelsen var ikke av type AvbruttHendelse for sak $sakId og hendelse $hendelseId")
                }

        val behandlingId = avsluttHendelse.id
        val behandling = (sak.behandlinger.tilbakekrevinger.hent(behandlingId) as? AvbruttTilbakekrevingsbehandling)
            ?: return Unit.also {
                log.error(
                    "Feil under generering av avbrutt-dokument: Fant ikke behandling $behandlingId, eller var ikke i AvbruttTilbakekrevingsbehandling tilstand, for sak $sakId og hendelse $hendelseId",
                )
            }

        when (avsluttHendelse.brevvalg) {
            is Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev -> return Unit.also {
                hendelsekonsumenterRepo.lagre(hendelseId, konsumentId)
            }

            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev -> return Unit.also {
                log.error("Feil under generering av avbrutt-dokument: Kunne ikke lage dokument for sak $sakId, hendelse $hendelseId og behandling $behandlingId. Underliggende feil: Skal ikke kunne sende vedtaksbrev")
            }

            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst -> {
                tilbakekrevingsbehandlingRepo.hentForSak(sakId).hentDokumenterForHendelseId(hendelseId).let {
                    if (it.isNotEmpty()) {
                        val ider = it.map { it.hendelseId }
                        return Unit.also {
                            hendelsekonsumenterRepo.lagre(hendelseId, konsumentId)
                            log.error("Feil under generering av avbrutt-dokument: Fant dokumenter knyttet til hendelsen. For sak $sakId og hendelse $hendelseId og dokumenthendelser $ider")
                        }
                    }
                }

                opprettDokumentForAvbruttBehandling(
                    avbruttHendelse = avsluttHendelse,
                    behandling = behandling,
                    nesteVersjon = nesteVersjon,
                    sakInfo = sak.info(),
                    correlationId = correlationId,
                ).map {
                    sessionFactory.withTransactionContext { context ->
                        dokumentHendelseRepo.lagre(it.first, it.second, context)
                        hendelsekonsumenterRepo.lagre(avsluttHendelse.hendelseId, konsumentId, context)
                    }
                }.mapLeft {
                    log.error("Feil under generering av avbrutt-dokument: Kunne ikke lage dokument for sak $sakId, hendelse $hendelseId og behandling $behandlingId. Underliggende feil: $it")
                }
            }
        }
    }

    private fun opprettDokumentForAvbruttBehandling(
        avbruttHendelse: AvbruttHendelse,
        behandling: AvbruttTilbakekrevingsbehandling,
        nesteVersjon: Hendelsesversjon,
        sakInfo: SakInfo,
        correlationId: CorrelationId,
    ): Either<KunneIkkeLageDokument, Pair<GenerertDokumentHendelse, HendelseFil>> {
        val command = AvbruttTilbakekrevingsbehandlingDokumentCommand(
            sakId = sakInfo.sakId,
            fødselsnummer = sakInfo.fnr,
            saksnummer = sakInfo.saksnummer,
            fritekst = (avbruttHendelse.brevvalg as Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst).fritekst,
            saksbehandler = avbruttHendelse.utførtAv,
            correlationId = correlationId,
        )

        val dokument = brevService.lagDokument(command = command)
            .getOrElse { return it.left() }
            .leggTilMetadata(
                Dokument.Metadata(
                    sakId = sakInfo.sakId,
                    tilbakekrevingsbehandlingId = avbruttHendelse.id.value,
                ),
            )

        val dokumentHendelse = behandling.lagDokumentHendelse(
            command = command,
            dokumentMedMetadataUtenFil = dokument.tilDokumentUtenFil(),
            nesteVersjon = nesteVersjon,
            relaterteHendelse = avbruttHendelse.hendelseId,
            clock = clock,
        )

        val hendelseFil = HendelseFil(hendelseId = dokumentHendelse.hendelseId, fil = dokument.generertDokument)

        return (dokumentHendelse to hendelseFil).right()
    }
}
