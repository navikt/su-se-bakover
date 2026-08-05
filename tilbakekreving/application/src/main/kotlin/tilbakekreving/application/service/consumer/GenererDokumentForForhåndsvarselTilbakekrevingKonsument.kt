package tilbakekreving.application.service.consumer

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Brevtype
import dokument.domain.Dokument
import dokument.domain.DokumentMedMetadataUtenFil.Companion.tilDokumentUtenFil
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.BrevService
import dokument.domain.hendelser.DokumentHendelseRepo
import dokument.domain.hendelser.GenerertDokumentHendelse
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.domain.extensions.mapOneIndexed
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.mottaker.MottakerIdentifikator
import no.nav.su.se.bakover.domain.mottaker.MottakerService
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import org.slf4j.LoggerFactory
import tilbakekreving.domain.ForhåndsvarsletTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.KanForhåndsvarsle
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.infrastructure.repo.ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype
import java.time.Clock
import java.util.UUID

/**
 * @see ForhåndsvarsletTilbakekrevingsbehandlingHendelse hendelsen som trigget konsumenten.
 */
class GenererDokumentForForhåndsvarselTilbakekrevingKonsument(
    private val sakService: SakService,
    private val brevService: BrevService,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val mottakerService: MottakerService,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : Hendelseskonsument {

    override val konsumentId = HendelseskonsumentId("GenererDokumentForForhåndsvarselTilbakekrevingKonsument")
    private val log = LoggerFactory.getLogger(this::class.java)

    fun genererDokumenter(correlationId: CorrelationId) {
        Either.catch {
            hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgTypeTilbakekreving(
                konsumentId = konsumentId,
                hendelsestype = ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype,
            ).forEach { (sakId, hendelsesIder) -> genererDokumenterForSak(sakId, hendelsesIder, correlationId) }
        }.mapLeft {
            log.error(
                "Kunne ikke generere dokument for forhåndsvarsel av tilbakekrevingsbehandling: Det ble kastet en exception for konsument $konsumentId",
                it,
            )
        }
    }

    private fun genererDokumenterForSak(
        sakId: UUID,
        hendelsesIder: Nel<HendelseId>,
        correlationId: CorrelationId,
    ) {
        val sak = sakService.hentSak(sakId)
            .getOrElse {
                log.error("Feil under generering av forhåndsvarseldokumenter: Kunne ikke hente sak $sakId")
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

        tilbakekrevingsbehandlingRepo.hentForSak(sakId).hentDokumenterForHendelseId(hendelseId).let {
            if (it != null) {
                return Unit.also {
                    hendelsekonsumenterRepo.lagre(hendelseId, konsumentId)
                    log.error("Feil under generering av forhåndsvarseldokument: Fant dokumenter knyttet til hendelsen. Dor sak $sakId og hendelse $hendelseId")
                }
            }
        }

        val forhåndsvarsleHendelse =
            (tilbakekrevingsbehandlingRepo.hentHendelse(hendelseId) as? ForhåndsvarsletTilbakekrevingsbehandlingHendelse)
                ?: return Unit.also {
                    log.error("Feil under generering av forhåndsvarseldokument: hendelsen var ikke av type ForhåndsvarsleTilbakekrevingsbehandlingHendelse for sak $sakId og hendelse $hendelseId")
                }

        val behandlingId = forhåndsvarsleHendelse.id
        val hentetBehandling = sak.behandlinger.tilbakekrevinger.hent(behandlingId) ?: return Unit.also {
            log.error(
                "Feil under generering av forhåndsvarseldokument: Fant ikke behandling $behandlingId for sak $sakId og hendelse $hendelseId",
            )
        }
        val behandling = (hentetBehandling as? KanForhåndsvarsle)
            ?: return Unit.also {
                log.error(
                    "Feil under generering av forhåndsvarseldokument: Behandling: $behandlingId, var ikke i KanForhåndsvarsle tilstand, tilstand: ${hentetBehandling.javaClass.simpleName} for sak $sakId og hendelse $hendelseId",
                )
            }

        opprettDokumentForForhåndsvarsel(
            forhåndsvarsleHendelse = forhåndsvarsleHendelse,
            behandling = behandling,
            nesteVersjon = nesteVersjon,
            sakInfo = sak.info(),
            varselHendelseId = hendelseId,
            correlationId = correlationId,
        ).map {
            sessionFactory.withTransactionContext { context ->
                dokumentHendelseRepo.lagreGenerertDokumentHendelse(
                    hendelse = it.first,
                    hendelseFil = it.second,
                    meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
                    sessionContext = context,
                )
                hendelsekonsumenterRepo.lagre(forhåndsvarsleHendelse.hendelseId, konsumentId, context)
            }
        }.mapLeft {
            log.error("Feil under generering av forhåndsvarseldokument: Kunne ikke lage dokument for sak $sakId, hendelse $hendelseId og behandling $behandlingId. Underliggende feil: $it")
        }
    }

    private fun opprettDokumentForForhåndsvarsel(
        forhåndsvarsleHendelse: ForhåndsvarsletTilbakekrevingsbehandlingHendelse,
        behandling: KanForhåndsvarsle,
        nesteVersjon: Hendelsesversjon,
        sakInfo: SakInfo,
        varselHendelseId: HendelseId,
        correlationId: CorrelationId,
    ): Either<KunneIkkeLageDokument, Pair<GenerertDokumentHendelse, HendelseFil>> {
        val dødsbo = mottakerService.hentMottaker(
            mottakerIdentifikator = MottakerIdentifikator(
                ReferanseTypeMottaker.DØDSBO_TILBAKEKREVING,
                referanseId = varselHendelseId.value,
                brevtype = Brevtype.FORHANDSVARSEL,
            ),
            sakId = sakInfo.sakId,
        ).getOrElse {
            return KunneIkkeLageDokument.FeilVedGenereringAvPdf.left()
        }

        val command = ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand(
            sakId = sakInfo.sakId,
            fødselsnummer = sakInfo.fnr,
            sakstype = sakInfo.type,
            saksnummer = sakInfo.saksnummer,
            fritekst = forhåndsvarsleHendelse.fritekst,
            saksbehandler = forhåndsvarsleHendelse.utførtAv,
            correlationId = correlationId,
            kravgrunnlag = behandling.kravgrunnlag,
            dødsbo = dødsbo != null,
        )

        val dokument = brevService.lagDokumentPdf(id = forhåndsvarsleHendelse.dokumentId, command = command)
            .getOrElse { return it.left() }
            .leggTilMetadata(
                Dokument.Metadata(
                    sakId = sakInfo.sakId,
                    tilbakekrevingsbehandlingId = forhåndsvarsleHendelse.id.value,
                ),
                // default er bruker om dødsbo ikke finnes
                distribueringsadresse = dødsbo?.adresse,
            )

        val dokumentHendelse = behandling.lagDokumenthendelseForForhåndsvarsel(
            command = command,
            dokumentMedMetadataUtenFil = dokument.tilDokumentUtenFil(),
            nesteVersjon = nesteVersjon,
            relaterteHendelse = forhåndsvarsleHendelse.hendelseId,
            clock = clock,
        )

        val hendelseFil = HendelseFil(hendelseId = dokumentHendelse.hendelseId, fil = dokument.generertDokument)

        return (dokumentHendelse to hendelseFil).right()
    }
}
