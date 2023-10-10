package no.nav.su.se.bakover.service.tilbakekreving

import dokument.domain.Dokument
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingClient
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingRepo
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.brev.lagDokumentKommando
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import no.nav.su.se.bakover.service.vedtak.VedtakService
import org.slf4j.LoggerFactory
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.RåTilbakekrevingsvedtakForsendelse
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import java.time.Clock
import java.util.UUID

interface TilbakekrevingService {
    /**
     * Lagrer et nytt kravgrunnlag vi har mottatt fra Oppdrag.
     */
    fun lagre(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag)

    /**
     * Sender utestående tilbakekrevings-avgjørelser til Oppdrag, så fremt vi har tilstrekkelig data.
     */
    fun sendTilbakekrevingsvedtak(mapper: (RåttKravgrunnlag) -> Kravgrunnlag)

    fun hentAvventerKravgrunnlag(sakId: UUID): List<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>
    fun hentAvventerKravgrunnlag(utbetalingId: UUID30): Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag?
    fun hentAvventerKravgrunnlag(): List<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>
}

class TilbakekrevingServiceImpl(
    private val tilbakekrevingRepo: TilbakekrevingRepo,
    private val tilbakekrevingClient: TilbakekrevingClient,
    private val vedtakService: VedtakService,
    private val brevService: BrevService,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
    private val satsFactory: SatsFactory,
) : TilbakekrevingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagre(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag) {
        return tilbakekrevingRepo.lagre(tilbakekrevingsbehandling)
    }

    /**
     * Ved å ta inn en mapper gjør det at vi slipper lagre den serialiserte versjonen i databasen samtidig som vi i større grad skiller domenet fra infrastruktur.
     */
    override fun sendTilbakekrevingsvedtak(
        mapper: (RåttKravgrunnlag) -> Kravgrunnlag,
    ) {
        tilbakekrevingRepo.hentMottattKravgrunnlag()
            .forEach { tilbakekrevingsbehandling ->
                val tilbakekrevingsvedtak = tilbakekrevingsbehandling.lagTilbakekrevingsvedtak(mapper)
                val vedtak =
                    vedtakService.hentForRevurderingId(tilbakekrevingsbehandling.avgjort.revurderingId)!! as Stønadsvedtak

                val dokument: Dokument.MedMetadata? = if (!vedtak.skalGenerereDokumentVedFerdigstillelse()) {
                    null
                } else {
                    val revurdering = vedtak.behandling as Revurdering
                    revurdering.lagDokumentKommando(satsFactory = satsFactory, clock = clock).let {
                        if (tilbakekrevingsbehandling.skalTilbakekreve().isRight()) {
                            check(it is IverksettRevurderingDokumentCommand.TilbakekrevingAvPenger) {
                                "Generert tilbakekrevingsbrev for vedtak:${vedtak.id} er ikke et tilbakekrevingsbrev! Var: ${it::class.simpleName}}"
                            }
                            it.erstattBruttoMedNettoFeilutbetaling(tilbakekrevingsvedtak.netto())
                        } else {
                            it
                        }
                    }.let { brevRequestMedNettoHvisTilbakekreving ->
                        brevService.lagDokument(brevRequestMedNettoHvisTilbakekreving)
                            .fold(
                                {
                                    throw RuntimeException("Kunne ikke lage dokument, feil: $it")
                                },
                                {
                                    it.leggTilMetadata(
                                        Dokument.Metadata(
                                            sakId = vedtak.behandling.sakId,
                                            vedtakId = vedtak.id,
                                            revurderingId = vedtak.behandling.id,
                                        ),
                                    )
                                },
                            )
                    }
                }

                /**
                 * Litt underlig logikk for lagring for å sikre at vi ikke havner i utakt, både mot økonomi og internt.
                 * 1. Opprett transaksjon
                 * 2. Lagre at vi har sendt tilbakekrevingsvedtak med tom data for den faktiske requesten som ble sendt.
                 * 3. Lagre dokument
                 * 4. Send tilbakekrevingsvedtak
                 * 5. Hvis sending feilet - kast exception rull tilbake foregående steg
                 *    Hvis sending gikk bra - commit transaksjon
                 * 6. Forsøk å oppdater steg 2. med faktisk request/response i egen transaksjon.
                 */
                val råTilbakekrevingsvedtakForsendelse = sessionFactory.withTransactionContext { tx ->
                    tilbakekrevingRepo.lagre(
                        tilbakekrevingsbehandling.sendtTilbakekrevingsvedtak(
                            tilbakekrevingsvedtakForsendelse = RåTilbakekrevingsvedtakForsendelse(
                                requestXml = "",
                                tidspunkt = Tidspunkt.now(clock),
                                responseXml = "",
                            ),
                        ),
                        transactionContext = tx,
                    )
                    dokument?.let {
                        brevService.lagreDokument(
                            dokument = dokument,
                            transactionContext = tx,
                        )
                    }

                    tilbakekrevingClient.sendTilbakekrevingsvedtak(tilbakekrevingsvedtak)
                        .fold(
                            {
                                throw RuntimeException("Feil ved oversendelse av tilbakekrevingsvedtak for tilbakekrevingsbehandling:${tilbakekrevingsbehandling.avgjort.id}, revurdering: ${tilbakekrevingsbehandling.avgjort.revurderingId}, feil:$it")
                            },
                            {
                                log.info("Besvart kravgrunnlag for tilbakekrevingsbehandling: ${tilbakekrevingsbehandling.avgjort.id}, revurdering: ${tilbakekrevingsbehandling.avgjort.revurderingId}")
                                it
                            },
                        )
                }

                try {
                    tilbakekrevingRepo.lagre(
                        tilbakekrevingsbehandling.sendtTilbakekrevingsvedtak(
                            tilbakekrevingsvedtakForsendelse = råTilbakekrevingsvedtakForsendelse,
                        ),
                    )
                } catch (ex: Throwable) {
                    sikkerLogg.info("Klarte ikke å oppdatere tilbakekrevingsbehandling: ${tilbakekrevingsbehandling.avgjort.id} med request/response xml. Innhold som ble forsøkt lagret: $råTilbakekrevingsvedtakForsendelse. Tilbakerevingsvedtak er sendt.")
                    throw ex
                }
            }
    }

    override fun hentAvventerKravgrunnlag(sakId: UUID): List<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag> {
        return tilbakekrevingRepo.hentAvventerKravgrunnlag(sakId)
    }

    override fun hentAvventerKravgrunnlag(utbetalingId: UUID30): Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag? {
        return tilbakekrevingRepo.hentAvventerKravgrunnlag(utbetalingId)
    }

    override fun hentAvventerKravgrunnlag(): List<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag> {
        return tilbakekrevingRepo.hentAvventerKravgrunnlag()
    }
}
