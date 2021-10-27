package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import no.nav.su.se.bakover.common.Tidspunkt
import java.time.Clock
import java.util.UUID

/**
 * Det rå/userialiserte kravgrunnlaget vi mottok fra Oppdrag.
 * Benytt [Kravgrunnlag] dersom du trenger selve innholdet i kravgrunnlaget.
 */
sealed class RåttKravgrunnlag {

    /** Vår genererte ID */
    abstract val id: UUID

    /** Tidspunktet vi mottok meldingen / leste den fra køen */
    abstract val opprettet: Tidspunkt

    /** Den rå meldingen (XML) vi mottok på køen */
    abstract val melding: String

    companion object {
        /**
         * Når vi får en ny Kravmelding
         */
        fun ny(melding: String, clock: Clock): Ubehandlet {
            return Ubehandlet(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                melding = melding,
            )
        }
    }

    /**
     * En ubehandlet kravmelding
     */
    data class Ubehandlet(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val melding: String,
    ) : RåttKravgrunnlag() {

        fun tilFerdigbehandlet(): Ferdigbehandlet {
            return Ferdigbehandlet(
                id = id,
                opprettet = opprettet,
                melding = melding,
            )
        }

        companion object {
            /**
             * Reservert til å kunne mappe fra databaselaget til domenemodellen.
             */
            fun persistert(
                id: UUID,
                opprettet: Tidspunkt,
                melding: String,
            ): RåttKravgrunnlag {
                return Ubehandlet(
                    id = id,
                    opprettet = opprettet,
                    melding = melding,
                )
            }
        }
    }

    /**
     * En ferdigbehandlet kravmelding.
     * TODO jah: Test en dobbel revurdering der en feilutbetaling vil overskrive den forrige feilutbetalingen. Sjekk om vedtaks-id da er lik og på eventuelle statuser om en kravmelding er avslått/avvist.
     */
    data class Ferdigbehandlet(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val melding: String,
    ) : RåttKravgrunnlag() {
        companion object {
            /**
             * Reservert til å kunne mappe fra databaselaget til domenemodellen.
             */
            fun persistert(
                id: UUID,
                opprettet: Tidspunkt,
                melding: String,
            ): RåttKravgrunnlag {
                return Ubehandlet(
                    id = id,
                    opprettet = opprettet,
                    melding = melding,
                )
            }
        }
    }
}
