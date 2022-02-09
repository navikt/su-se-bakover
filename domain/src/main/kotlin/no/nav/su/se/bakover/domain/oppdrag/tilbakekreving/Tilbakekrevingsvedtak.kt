package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import arrow.core.Either
import arrow.core.right

/** Beslutningsgrunnlaget til et kravgrunnlag som sendes til Oppdrag. */
data class Tilbakekrevingsvedtak private constructor(

    /** Kravgrunnlaget fra kravmeldingen som vi skal ta en avgjørelse på. */
    val kravgrunnlag: Kravgrunnlag,

    val tilbakekrevingsbehandling: Tilbakekrevingsbehandling,

) {
    companion object {
        /**
         * @param kravgrunnlag Kravgrunnlaget fra kravmeldingen som vi skal ta en avgjørelse på.
         * @param tilbakekrevingsbehandling Det finnes også en behandler på kravmeldinga (som kommer fra vedtaket/utbetalingslinjene). Disse må stemme overens. Vi skal ikke skille på undergruppene av NavIdentBruker.
         */
        fun tryCreate(
            kravgrunnlag: Kravgrunnlag,
            tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet.MottattKravgrunnlag,
        ): Either<UlikBehandlerIVedtakOgKravgrunnlag, Tilbakekrevingsvedtak> {
            // TODO jah: Verifiser at tilbakekrevingsperiodene stemmer overens med kravgrunnlaget
            return Tilbakekrevingsvedtak(
                kravgrunnlag = kravgrunnlag,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling,
            ).right()
        }
    }

    object UlikBehandlerIVedtakOgKravgrunnlag
}
