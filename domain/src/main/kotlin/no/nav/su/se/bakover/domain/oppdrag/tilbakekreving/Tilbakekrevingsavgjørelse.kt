package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import java.util.UUID

/**
 * Dersom en revurdering fører til til en feilutbetaling, må vi ta stilling til om vi skal kreve tilbake eller ikke.
 *
 * @property periode Vi støtter i førsteomgang kun en sammenhengende periode, som kan være hele eller deler av en revurderingsperiode.
 * @property oversendtTidspunkt Tidspunktet vi sendte avgjørelsen til oppdrag, ellers null
 */
sealed class Tilbakekrevingsavgjørelse {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val sakId: UUID
    abstract val revurderingId: UUID
    abstract val periode: Periode
    abstract val oversendtTidspunkt: Tidspunkt?

    /**
     * Vi ønsker å skille på forsto og burde forstått i domenet til SU.
     */
    sealed class SkalTilbakekreve : Tilbakekrevingsavgjørelse() {
        data class Forsto(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val revurderingId: UUID,
            override val periode: Periode,
            override val oversendtTidspunkt: Tidspunkt?,
        ) : SkalTilbakekreve()

        data class BurdeForstått(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val revurderingId: UUID,
            override val periode: Periode,
            override val oversendtTidspunkt: Tidspunkt?,
        ) : SkalTilbakekreve()
    }

    /** Dette er likestilt med at bruker ikke forsto eller kunne ha forstått */
    data class SkalIkkeTilbakekreve(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val revurderingId: UUID,
        override val periode: Periode,
        override val oversendtTidspunkt: Tidspunkt?,
    ) : Tilbakekrevingsavgjørelse()
}
