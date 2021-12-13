package no.nav.su.se.bakover.domain.avkorting

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.time.LocalDate
import java.util.UUID

sealed interface Avkortingsvarsel {

    sealed interface Utenlandsopphold : Avkortingsvarsel {
        val id: UUID
        val sakId: UUID
        val revurderingId: UUID
        val opprettet: Tidspunkt
        val simulering: Simulering
        val feilutbetalingslinje: Feilutbetalingslinje

        fun hentUtbetalteBeløp(): List<Pair<Periode, Int>> {
            return simulering.hentUtbetalteBeløp()
        }

        data class Opprettet(
            override val id: UUID,
            override val sakId: UUID,
            override val revurderingId: UUID,
            override val opprettet: Tidspunkt,
            override val simulering: Simulering,
            override val feilutbetalingslinje: Feilutbetalingslinje,
        ) : Utenlandsopphold {
            constructor(
                sakId: UUID,
                revurderingId: UUID,
                simulering: Simulering,
                utbetalingslinje: Utbetalingslinje.Endring.Opphør,
            ) : this(
                id = UUID.randomUUID(),
                sakId = sakId,
                revurderingId = revurderingId,
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
            fun avkortet(søknadsbehandlingId: UUID): Avkortet {
                return Avkortet(this, søknadsbehandlingId)
            }
        }

        data class Avkortet(
            private val objekt: SkalAvkortes,
            val søknadsbehandlingId: UUID,
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
