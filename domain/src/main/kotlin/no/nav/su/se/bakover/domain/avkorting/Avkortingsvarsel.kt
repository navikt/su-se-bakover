package no.nav.su.se.bakover.domain.avkorting

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.Månedsbeløp
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel.Utenlandsopphold.Annullert
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel.Utenlandsopphold.Avkortet
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel.Utenlandsopphold.Opprettet
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel.Utenlandsopphold.SkalAvkortes
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID

sealed interface Avkortingsvarsel {

    /**
     * Representerer et varsel om at vi har identifisert et tilfelle av feilutbetaling som skal/kan/bør avkortes en gang i fremtiden,
     * typisk i neste stønadsperiode.
     *
     * De ulike tilstandene en overordnet "nå" tilstand for et enkelt tilfelle skal/kan/bør avkortes.
     *
     * @see Opprettet
     * @see SkalAvkortes
     * @see Avkortet
     * @see Annullert
     */
    sealed interface Utenlandsopphold : Avkortingsvarsel {
        val id: UUID
        val sakId: UUID
        val revurderingId: UUID
        val opprettet: Tidspunkt
        val simulering: Simulering

        fun hentUtbetalteBeløp(): Månedsbeløp {
            return simulering.hentUtbetalteBeløp()
        }

        /**
         * Initiell tilstand for et varsel om avkorting. Vi har identifisert gjennom simulering at penger er feilutbetalt,
         * samt at dette skyldes utenlandsopphold.
         * TODO: Refaktorer bort denne tilstanden, kan gå direkte til neste tilstand.
         */
        data class Opprettet(
            override val id: UUID = UUID.randomUUID(),
            override val sakId: UUID,
            override val revurderingId: UUID,
            override val opprettet: Tidspunkt,
            override val simulering: Simulering,
        ) : Utenlandsopphold {

            fun skalAvkortes(): SkalAvkortes {
                return SkalAvkortes(this)
            }
        }

        /**
         * Vi har bestemt oss for at beløpene som er feilutbetalt i [simulering] skal avkortes i fremtiden.
         * Tilstanden er en "ventetilstand" som brukes for å sjekke om det eksisterer utestående avkortinger som skal
         * hensyntas ved saksbehandling.
         *
         * @see AvkortingVedSøknadsbehandling
         * @see AvkortingVedRevurdering
         */
        data class SkalAvkortes(
            private val objekt: Opprettet,
        ) : Utenlandsopphold by objekt {
            fun avkortet(behandlingId: UUID): Avkortet {
                return Avkortet(this, behandlingId)
            }

            fun annuller(behandlingId: UUID): Annullert {
                return Annullert(this, behandlingId)
            }

            fun fullstendigAvkortetAv(beregning: Beregning): Boolean {
                val beløpSkalAvkortes = simulering.hentUtbetalteBeløp().sum()
                val fradragAvkorting = beregning.getFradrag()
                    .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                    .sumOf { it.månedsbeløp }
                    .toInt()
                return beløpSkalAvkortes == fradragAvkorting
            }

            fun periode(): Periode = simulering.hentUtbetalteBeløp().månedbeløp.map {
                it.periode
            }.let {
                Periode.create(it.minOf { it.fraOgMed }, it.maxOf { it.tilOgMed })
            }
        }

        /**
         * Sluttilstand for tilfeller hvor vi har klart å gjennomføre avkorting av det opprinnelige varselet.
         */
        data class Avkortet(
            private val objekt: SkalAvkortes,
            val behandlingId: UUID,
        ) : Utenlandsopphold by objekt

        /**
         * Sluttilstand for tilfeller hvor vi har annullert det opprinnelige varselet. Kan oppstå dersom man
         * revurderer en periode med utestående varsel.
         */
        data class Annullert(
            private val objekt: SkalAvkortes,
            val behandlingId: UUID,
        ) : Utenlandsopphold by objekt
    }

    object Ingen : Avkortingsvarsel
}
