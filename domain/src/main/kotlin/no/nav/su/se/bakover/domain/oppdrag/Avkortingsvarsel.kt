package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.time.LocalDate
import java.util.UUID

sealed interface Avkortingsvarsel {

    sealed interface Utenlandsopphold : Avkortingsvarsel {
        val id: UUID
        val opprettet: Tidspunkt
        val simulering: Simulering
        val feilutbetalingslinje: Feilutbetalingslinje

        fun hentUtbetalteBeløp(periode: Periode): List<Pair<Periode, Int>> {
            return simulering.hentUtbetalteBeløp(periode)
        }

        data class Opprettet(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val simulering: Simulering,
            override val feilutbetalingslinje: Feilutbetalingslinje,
        ) : Utenlandsopphold {
            constructor(
                simulering: Simulering,
                utbetalingslinje: Utbetalingslinje.Endring.Opphør,
            ) : this(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                simulering = simulering,
                feilutbetalingslinje = Feilutbetalingslinje(
                    fraOgMed = utbetalingslinje.fraOgMed,
                    tilOgMed = utbetalingslinje.tilOgMed,
                    forrigeUtbetalingslinjeId = utbetalingslinje.forrigeUtbetalingslinjeId,
                    beløp = utbetalingslinje.beløp,
                    virkningstidspunkt = utbetalingslinje.virkningstidspunkt,
                    uføregrad = utbetalingslinje.uføregrad,
                ),
            )

            fun skalAvkortes(): SkalAvkortes {
                return SkalAvkortes(this)
            }
        }

        data class SkalAvkortes(
            private val objekt: Opprettet,
        ) : Utenlandsopphold by objekt {
            fun avkortet(): Avkortet {
                return Avkortet(this)
            }
        }

        data class Avkortet(
            private val objekt: SkalAvkortes,
        ) : Utenlandsopphold by objekt

        data class Feilutbetalingslinje(
            val fraOgMed: LocalDate,
            val tilOgMed: LocalDate,
            var forrigeUtbetalingslinjeId: UUID30?,
            val beløp: Int,
            val virkningstidspunkt: LocalDate,
            val uføregrad: Uføregrad?,
        )
    }

    object Ingen : Avkortingsvarsel
}
