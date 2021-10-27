package no.nav.su.se.bakover.service.tilbakekreving

import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Kravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingClient
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingRepo
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsvedtak

interface TilbakekrevingService {
    /**
     * Lagrer et nytt kravgrunnlag vi har mottatt fra Oppdrag.
     */
    fun lagreKravgrunnlag(kravgrunnlag: RåttKravgrunnlag)

    /**
     * Sender utestående tilbakekrevings-avgjørelser til Oppdrag, så fremt vi har tilstrekkelig data.
     */
    fun sendTilbakekrevinger(mapper: (RåttKravgrunnlag) -> Kravgrunnlag)
}

class TilbakekrevingServiceImpl(
    private val tilbakekrevingRepo: TilbakekrevingRepo,
    private val tilbakekrevingClient: TilbakekrevingClient,
) : TilbakekrevingService {

    override fun lagreKravgrunnlag(kravgrunnlag: RåttKravgrunnlag) {
        return tilbakekrevingRepo.lagreKravgrunnlag(kravgrunnlag)
    }

    /**
     * Ved å ta inn en mapper gjør det at vi slipper lagre den serialiserte versjonen i databasen samtidig som vi i større grad skiller domenet fra infrastruktur.
     */
    override fun sendTilbakekrevinger(mapper: (RåttKravgrunnlag) -> Kravgrunnlag) {
        tilbakekrevingRepo.hentUbehandlaKravgrunnlag().forEach {
            tilbakekrevingClient.avgjørKravgrunnlag(
                Tilbakekrevingsvedtak.tryCreate(
                    kravgrunnlag = mapper(it),
                    tilbakekrevingsavgjørelse = tilbakekrevingRepo.hentTilbakekrevingsavgjørelse // TODO jah: Må knytte kravgrunnlaget til en revurdering/vedtak. Hente saken?
                ),
            )
        }
    }
}
