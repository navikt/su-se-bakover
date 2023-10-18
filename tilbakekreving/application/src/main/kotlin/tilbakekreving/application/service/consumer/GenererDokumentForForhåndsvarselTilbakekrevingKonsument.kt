package tilbakekreving.application.service.consumer

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.DokumentHendelseRepo
import dokument.domain.DokumentMedMetadataUtenFil.Companion.tilDokumentUtenFil
import dokument.domain.LagretDokumentHendelse
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import org.slf4j.LoggerFactory
import tilbakekreving.domain.ForhåndsvarsleTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.KanForhåndsvarsle
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.forhåndsvarsel.KunneIkkeGenerereDokumentForForhåndsvarsel
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.infrastructure.repo.ForhåndsvarsleTilbakekrevingsbehandlingHendelsestype
import java.time.Clock
import java.util.UUID

class GenererDokumentForForhåndsvarselTilbakekrevingKonsument(
    private val sakService: SakService,
    private val brevService: BrevService,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
    private val hendelseRepo: HendelseRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : Hendelseskonsument {

    override val konsumentId = HendelseskonsumentId("GenererDokumentForForhåndsvarselTilbakekrevingKonsument")
    private val log = LoggerFactory.getLogger(this::class.java)

    fun genererDokumenter(correlationId: CorrelationId) {
        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = ForhåndsvarsleTilbakekrevingsbehandlingHendelsestype,
        ).forEach { (sakId, hendelsesIder) -> prosesserSak(sakId, hendelsesIder, correlationId) }
    }

    private fun prosesserSak(
        sakId: UUID,
        hendelsesIder: Nel<HendelseId>,
        correlationId: CorrelationId,
    ) {
        val sak = sakService.hentSak(sakId)
            .getOrElse { throw IllegalStateException("Kunne ikke hente sakInfo $sakId for å generering av dokument for ForhåndsvarsleTilbakekrevingsbehandlingHendelse") }

        hendelsesIder.map { relatertHendelsesId ->
            val nesteVersjon = hendelseRepo.hentSisteVersjonFraEntitetId(sak.id)?.inc()
                ?: throw IllegalStateException("Kunne ikke hente siste versjon for sak ${sak.id} for å generere dokument")

            val relatertHendelse =
                (tilbakekrevingsbehandlingRepo.hentHendelse(relatertHendelsesId) as? ForhåndsvarsleTilbakekrevingsbehandlingHendelse)
                    ?: throw IllegalStateException("Feil ved henting av hendelse for å generere dokument. sak $sakId, hendelse $relatertHendelsesId")

            val behandling = (sak.behandlinger.tilbakekrevinger.hent(relatertHendelse.id) as? KanForhåndsvarsle)
                ?: throw IllegalStateException(
                    "Fant ikke behandling, eller var ikke i KanForhåndsvarsle tilstand, for hendelse ${relatertHendelse.id} for sak ${sak.id}",
                )

            opprettDokumentForForhåndsvarsel(
                forhåndsvarsletHendelse = relatertHendelse,
                behandling = behandling,
                nesteVersjon = nesteVersjon,
                sakInfo = sak.info(),
                correlationId = correlationId,
            ).map {
                sessionFactory.withTransactionContext { context ->
                    dokumentHendelseRepo.lagre(it.first, it.second, context)
                    hendelsekonsumenterRepo.lagre(relatertHendelse.hendelseId, konsumentId, context)
                }
            }.mapLeft {
                log.error("Feil skjedde ved generering av dokument for ForhåndsvarsletTilbakekrevingsbehandlingHendelse $it. For sak $sakId, hendelse ${relatertHendelse.id}")
            }
        }
    }

    private fun opprettDokumentForForhåndsvarsel(
        forhåndsvarsletHendelse: ForhåndsvarsleTilbakekrevingsbehandlingHendelse,
        behandling: KanForhåndsvarsle,
        nesteVersjon: Hendelsesversjon,
        sakInfo: SakInfo,
        correlationId: CorrelationId,
    ): Either<KunneIkkeGenerereDokumentForForhåndsvarsel, Pair<LagretDokumentHendelse, HendelseFil>> {
        val command = ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand(
            sakId = sakInfo.sakId,
            fødselsnummer = sakInfo.fnr,
            saksnummer = sakInfo.saksnummer,
            fritekst = forhåndsvarsletHendelse.fritekst,
            saksbehandler = forhåndsvarsletHendelse.utførtAv,
            correlationId = correlationId,
        )

        val dokument = brevService.lagDokument(id = forhåndsvarsletHendelse.dokumentId, command = command)
            .getOrElse { return KunneIkkeGenerereDokumentForForhåndsvarsel.FeilVedDokumentGenerering(it).left() }
            .leggTilMetadata(Dokument.Metadata(sakId = sakInfo.sakId, tilbakekrevingsbehandlingId = forhåndsvarsletHendelse.id.value))

        val dokumentHendelse = behandling.nyLagretDokumentHendelse(
            command = command,
            dokumentMedMetadataUtenFil = dokument.tilDokumentUtenFil(clock),
            nesteVersjon = nesteVersjon,
            relaterteHendelser = nonEmptyListOf(forhåndsvarsletHendelse.hendelseId),
            clock = clock,
        )

        val hendelseFil = HendelseFil(hendelseId = dokumentHendelse.hendelseId, fil = dokument.generertDokument)

        return (dokumentHendelse to hendelseFil).right()
    }
}
