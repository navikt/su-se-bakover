package no.nav.su.se.bakover.service.tilbakekreving

import arrow.core.getOrHandle
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Kravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingClient
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingRepo
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsvedtak
import java.util.UUID

interface TilbakekrevingService {
    /**
     * Lagrer et nytt kravgrunnlag vi har mottatt fra Oppdrag.
     */
    fun lagreMottattKravgrunnlag(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet.MottattKravgrunnlag)

    /**
     * Sender utestående tilbakekrevings-avgjørelser til Oppdrag, så fremt vi har tilstrekkelig data.
     */
    fun sendTilbakekrevinger(mapper: (RåttKravgrunnlag) -> Kravgrunnlag)

    // TODO endre dette til å bruke henvisning/referanse send med utbetalingen
    fun hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(sakId: UUID): List<Tilbakekrevingsbehandling.Ferdigbehandlet.AvventerKravgrunnlag>
    fun hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(): List<Tilbakekrevingsbehandling.Ferdigbehandlet.AvventerKravgrunnlag>
}

class TilbakekrevingServiceImpl(
    private val tilbakekrevingRepo: TilbakekrevingRepo,
    private val tilbakekrevingClient: TilbakekrevingClient,
) : TilbakekrevingService {

    override fun lagreMottattKravgrunnlag(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet.MottattKravgrunnlag) {
        return tilbakekrevingRepo.lagreMottattKravgrunnlag(tilbakekrevingsbehandling)
    }

    /**
     * Ved å ta inn en mapper gjør det at vi slipper lagre den serialiserte versjonen i databasen samtidig som vi i større grad skiller domenet fra infrastruktur.
     */
    override fun sendTilbakekrevinger(mapper: (RåttKravgrunnlag) -> Kravgrunnlag) {
        tilbakekrevingRepo.hentTilbakekrevingsbehandlingerMedUbesvartKravgrunnlag().forEach {
            val mapped = mapper(it.kravgrunnlag)
            tilbakekrevingClient.avgjørKravgrunnlag(
                Tilbakekrevingsvedtak.tryCreate(
                    kravgrunnlag = mapped,
                    // TODO jah: Må knytte kravgrunnlaget til en revurdering/vedtak. Hente saken?
                    // TODO kan knyttes direkte mot et vedtak ved å sende med vedtakid som "henvisning" med utbetalingen, i kravgrunnlaget vil "referanse" inneholde verdien fra henvisning send med utbetalingen.
                    tilbakekrevingsbehandling = it,
                ).getOrHandle { throw IllegalStateException() },
            )
        }
    }

    override fun hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(sakId: UUID): List<Tilbakekrevingsbehandling.Ferdigbehandlet.AvventerKravgrunnlag> {
        return tilbakekrevingRepo.hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(sakId)
    }

    override fun hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(): List<Tilbakekrevingsbehandling.Ferdigbehandlet.AvventerKravgrunnlag> {
        return tilbakekrevingRepo.hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag()
    }
}
