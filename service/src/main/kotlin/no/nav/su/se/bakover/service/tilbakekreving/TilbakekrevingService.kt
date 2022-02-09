package no.nav.su.se.bakover.service.tilbakekreving

import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Kravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingClient
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingRepo
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsvedtak
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

interface TilbakekrevingService {
    /**
     * Lagrer et nytt kravgrunnlag vi har mottatt fra Oppdrag.
     */
    fun lagreMottattKravgrunnlag(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag)

    /**
     * Sender utestående tilbakekrevings-avgjørelser til Oppdrag, så fremt vi har tilstrekkelig data.
     */
    fun sendTilbakekrevinger(mapper: (RåttKravgrunnlag) -> Kravgrunnlag)

    // TODO endre dette til å bruke henvisning/referanse send med utbetalingen
    fun hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(sakId: UUID): List<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>
    fun hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(): List<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>
}

class TilbakekrevingServiceImpl(
    private val tilbakekrevingRepo: TilbakekrevingRepo,
    private val tilbakekrevingClient: TilbakekrevingClient,
    private val clock: Clock,
) : TilbakekrevingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagreMottattKravgrunnlag(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag) {
        return tilbakekrevingRepo.lagreMottattKravgrunnlag(tilbakekrevingsbehandling)
    }

    /**
     * Ved å ta inn en mapper gjør det at vi slipper lagre den serialiserte versjonen i databasen samtidig som vi i større grad skiller domenet fra infrastruktur.
     */
    override fun sendTilbakekrevinger(mapper: (RåttKravgrunnlag) -> Kravgrunnlag) {
        tilbakekrevingRepo.hentTilbakekrevingsbehandlingerMedUbesvartKravgrunnlag()
            .forEach { tilbakekrevingsbehandling ->
                val mapped = mapper(tilbakekrevingsbehandling.kravgrunnlag)
                tilbakekrevingClient.avgjørKravgrunnlag(
                    Tilbakekrevingsvedtak.tryCreate(
                        kravgrunnlag = mapped,
                        // TODO jah: Må knytte kravgrunnlaget til en revurdering/vedtak. Hente saken?
                        // TODO kan knyttes direkte mot et vedtak ved å sende med vedtakid som "henvisning" med utbetalingen, i kravgrunnlaget vil "referanse" inneholde verdien fra henvisning send med utbetalingen.
                        tilbakekrevingsbehandling = tilbakekrevingsbehandling,
                    ).getOrHandle { throw IllegalStateException() },
                ).map {
                    tilbakekrevingRepo.lagreKravgrunnlagBesvart(
                        tilbakekrevingsbehandling.kravgrunnlagBesvart(
                            kravgrunnlagBesvart = Tidspunkt.now(clock),
                        ),
                    )
                    log.info("Besvart kravgrunnlag for tilbakekrevingsbehandling: ${tilbakekrevingsbehandling.avgjort.id}, revurdering: ${tilbakekrevingsbehandling.avgjort.revurderingId}")
                }
            }
    }

    override fun hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(sakId: UUID): List<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag> {
        return tilbakekrevingRepo.hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(sakId)
    }

    override fun hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(): List<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag> {
        return tilbakekrevingRepo.hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag()
    }
}
