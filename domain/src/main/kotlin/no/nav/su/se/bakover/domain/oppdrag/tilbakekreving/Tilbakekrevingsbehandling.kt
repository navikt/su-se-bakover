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

data class Forsto(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val revurderingId: UUID,
    override val periode: Periode,
) : Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort,
    Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving {
    override fun ferdigbehandlet(): Tilbakekrevingsbehandling.Ferdigbehandlet.AvventerKravgrunnlag {
        return AvventerKravgrunnlag(this)
    }
}

data class BurdeForstått(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val revurderingId: UUID,
    override val periode: Periode,
) : Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort,
    Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving {
    override fun ferdigbehandlet(): Tilbakekrevingsbehandling.Ferdigbehandlet.AvventerKravgrunnlag {
        return AvventerKravgrunnlag(this)
    }
}

data class KunneIkkeForstå(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val revurderingId: UUID,
    override val periode: Periode,
) : Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort,
    Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving {
    override fun ferdigbehandlet(): Tilbakekrevingsbehandling.Ferdigbehandlet.AvventerKravgrunnlag {
        return AvventerKravgrunnlag(this)
    }
}

data class IkkeAvgjort(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val revurderingId: UUID,
    override val periode: Periode,
) : Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.IkkeAvgjort,
    Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving {
    fun forsto(): Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort {
        return Forsto(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            revurderingId = revurderingId,
            periode = periode,
        )
    }

    fun burdeForstått(): Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort {
        return BurdeForstått(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            revurderingId = revurderingId,
            periode = periode,
        )
    }

    fun kunneIkkeForstå(): Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort {
        return KunneIkkeForstå(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            revurderingId = revurderingId,
            periode = periode,
        )
    }
}

data class AvventerKravgrunnlag(
    override val avgjort: Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort,
) : Tilbakekrevingsbehandling.Ferdigbehandlet.AvventerKravgrunnlag {
    override fun mottattKravgrunnlag(
        kravgrunnlag: RåttKravgrunnlag,
        kravgrunnlagMottatt: Tidspunkt,
    ): Tilbakekrevingsbehandling.Ferdigbehandlet.MottattKravgrunnlag {
        return MottattKravgrunnlag(
            avgjort = avgjort,
            kravgrunnlag = kravgrunnlag,
            kravgrunnlagMottatt = kravgrunnlagMottatt,
        )
    }
}

data class MottattKravgrunnlag(
    override val avgjort: Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort,
    override val kravgrunnlag: RåttKravgrunnlag,
    override val kravgrunnlagMottatt: Tidspunkt,
) : Tilbakekrevingsbehandling.Ferdigbehandlet.MottattKravgrunnlag

object IkkeBehovForTilbakekrevingUnderBehandling :
    Tilbakekrevingsbehandling.UnderBehandling.IkkeBehovForTilbakekreving {
    override fun ferdigbehandlet(): Tilbakekrevingsbehandling.Ferdigbehandlet.IkkeBehovForTilbakekreving {
        return IkkeBehovForTilbakekrevingFerdigbehandlet
    }
}

object IkkeBehovForTilbakekrevingFerdigbehandlet : Tilbakekrevingsbehandling.Ferdigbehandlet.IkkeBehovForTilbakekreving

sealed interface Tilbakekrevingsbehandling {

    sealed interface UnderBehandling : Tilbakekrevingsbehandling {

        fun ferdigbehandlet(): Ferdigbehandlet

        sealed interface VurderTilbakekreving : UnderBehandling {
            val id: UUID
            val opprettet: Tidspunkt
            val sakId: UUID
            val revurderingId: UUID
            val periode: Periode

            sealed interface Avgjort : VurderTilbakekreving {
                override fun ferdigbehandlet(): Ferdigbehandlet.AvventerKravgrunnlag
            }

            sealed interface IkkeAvgjort : VurderTilbakekreving {
                override fun ferdigbehandlet(): Ferdigbehandlet.AvventerKravgrunnlag {
                    throw IllegalStateException("Må avgjøres før vurdering kan ferdigbehandles")
                }
            }
        }

        interface IkkeBehovForTilbakekreving : UnderBehandling {
            override fun ferdigbehandlet(): Ferdigbehandlet.IkkeBehovForTilbakekreving
        }
    }

    sealed interface Ferdigbehandlet : Tilbakekrevingsbehandling {

        interface IkkeBehovForTilbakekreving : Ferdigbehandlet

        sealed interface AvventerKravgrunnlag : Ferdigbehandlet {
            val avgjort: UnderBehandling.VurderTilbakekreving.Avgjort

            fun mottattKravgrunnlag(kravgrunnlag: RåttKravgrunnlag, kravgrunnlagMottatt: Tidspunkt): MottattKravgrunnlag
        }

        sealed interface MottattKravgrunnlag : Ferdigbehandlet {
            val avgjort: UnderBehandling.VurderTilbakekreving.Avgjort
            val kravgrunnlag: RåttKravgrunnlag
            val kravgrunnlagMottatt: Tidspunkt
        }
    }
}
