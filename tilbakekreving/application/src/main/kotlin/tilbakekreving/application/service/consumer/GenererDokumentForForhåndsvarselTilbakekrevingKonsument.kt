package tilbakekreving.application.service.consumer

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import dokument.domain.Dokument
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.domain.extensions.mapOneIndexed
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import org.slf4j.LoggerFactory
import tilbakekreving.domain.ForhåndsvarsletTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.infrastructure.repo.ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype
import java.util.UUID

/**
 * @see ForhåndsvarsletTilbakekrevingsbehandlingHendelse hendelsen som trigget konsumenten.
 */
class GenererDokumentForForhåndsvarselTilbakekrevingKonsument(
    private val sakService: SakService,
    private val brevService: BrevService,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val sessionFactory: SessionFactory,
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
        hendelsesIder.mapOneIndexed { _, hendelseId ->
            genererDokumentForSakOgHendelse(sak, hendelseId, correlationId)
        }
    }

    private fun genererDokumentForSakOgHendelse(
        sak: Sak,
        hendelseId: HendelseId,
        correlationId: CorrelationId,
    ) {
        val sakId = sak.id

        tilbakekrevingsbehandlingRepo.hentForSak(sakId).hentDokumenterForHendelseId(hendelseId).let {
            if (it != null) {
                return Unit.also {
                    hendelsekonsumenterRepo.lagre(hendelseId, konsumentId)
                    log.info("Fant historisk dokumenthendelse for sak $sakId og hendelse $hendelseId. Hopper over ny generering.")
                }
            }
        }

        val forhåndsvarsleHendelse =
            (tilbakekrevingsbehandlingRepo.hentHendelse(hendelseId) as? ForhåndsvarsletTilbakekrevingsbehandlingHendelse)
                ?: return Unit.also {
                    log.error("Feil under generering av forhåndsvarseldokument: hendelsen var ikke av type ForhåndsvarsleTilbakekrevingsbehandlingHendelse for sak $sakId og hendelse $hendelseId")
                }

        when (brevService.hentDokument(forhåndsvarsleHendelse.dokumentId)) {
            is Either.Right -> return Unit.also {
                hendelsekonsumenterRepo.lagre(hendelseId, konsumentId)
                log.info("Dokument ${forhåndsvarsleHendelse.dokumentId} finnes allerede. Hopper over generering for sak $sakId og hendelse $hendelseId")
            }
            is Either.Left -> Unit
        }

        val behandlingId = forhåndsvarsleHendelse.id
        val hentetBehandling = sak.behandlinger.tilbakekrevinger.hent(behandlingId) ?: return Unit.also {
            log.error(
                "Feil under generering av forhåndsvarseldokument: Fant ikke behandling $behandlingId for sak $sakId og hendelse $hendelseId",
            )
        }

        opprettDokumentForForhåndsvarsel(
            forhåndsvarsleHendelse = forhåndsvarsleHendelse,
            sakInfo = sak.info(),
            correlationId = correlationId,
            kravgrunnlag = hentetBehandling.kravgrunnlag,
        ).map {
            sessionFactory.withTransactionContext { context ->
                brevService.lagreDokument(it, context)
                hendelsekonsumenterRepo.lagre(forhåndsvarsleHendelse.hendelseId, konsumentId, context)
            }
        }.mapLeft {
            log.error("Feil under generering av forhåndsvarseldokument: Kunne ikke lage dokument for sak $sakId, hendelse $hendelseId og behandling $behandlingId. Underliggende feil: $it")
        }
    }

    private fun opprettDokumentForForhåndsvarsel(
        forhåndsvarsleHendelse: ForhåndsvarsletTilbakekrevingsbehandlingHendelse,
        sakInfo: SakInfo,
        correlationId: CorrelationId,
        kravgrunnlag: Kravgrunnlag?,
    ): Either<KunneIkkeLageDokument, Dokument.MedMetadata> {
        val command = ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand(
            sakId = sakInfo.sakId,
            fødselsnummer = sakInfo.fnr,
            sakstype = sakInfo.type,
            saksnummer = sakInfo.saksnummer,
            fritekst = forhåndsvarsleHendelse.fritekst,
            saksbehandler = forhåndsvarsleHendelse.utførtAv,
            correlationId = correlationId,
            kravgrunnlag = kravgrunnlag,
        )

        return brevService.lagDokumentPdf(id = forhåndsvarsleHendelse.dokumentId, command = command)
            .map {
                it.leggTilMetadata(
                    Dokument.Metadata(
                        sakId = sakInfo.sakId,
                        tilbakekrevingsbehandlingId = forhåndsvarsleHendelse.id.value,
                    ),
                    // kan ikke sende brev til en annen adresse enn brukerens adresse per nå
                    distribueringsadresse = null,
                )
            }
    }
}
