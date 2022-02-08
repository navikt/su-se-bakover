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
sealed interface Tilbakekrevingsbehandling {

    sealed interface VurderTilbakekreving : Tilbakekrevingsbehandling {
        val id: UUID
        val opprettet: Tidspunkt
        val sakId: UUID
        val revurderingId: UUID
        val periode: Periode

        sealed interface Avgjort : VurderTilbakekreving, FullstendigTilbakekrevingsbehandling {

            data class Forsto(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val revurderingId: UUID,
                override val periode: Periode,
            ) : Avgjort

            data class BurdeForstått(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val revurderingId: UUID,
                override val periode: Periode,
            ) : Avgjort

            data class KunneIkkeForstått(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val revurderingId: UUID,
                override val periode: Periode,
            ) : Avgjort
        }

        data class IkkeAvgjort(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val revurderingId: UUID,
            override val periode: Periode,
        ) : VurderTilbakekreving {
            fun forsto(): Avgjort.Forsto {
                return Avgjort.Forsto(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    revurderingId = revurderingId,
                    periode = periode,
                )
            }

            fun burdeForstått(): Avgjort.BurdeForstått {
                return Avgjort.BurdeForstått(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    revurderingId = revurderingId,
                    periode = periode,
                )
            }

            fun kunneIkkeForstå(): Avgjort.KunneIkkeForstått {
                return Avgjort.KunneIkkeForstått(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    revurderingId = revurderingId,
                    periode = periode,
                )
            }
        }
    }

    object IkkeBehovForTilbakekreving : Tilbakekrevingsbehandling, FullstendigTilbakekrevingsbehandling
}

sealed interface FullstendigTilbakekrevingsbehandling
