package no.nav.su.se.bakover.domain.avkorting

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Månedsbeløp
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID

sealed interface Avkortingsvarsel {

    sealed interface Utenlandsopphold : Avkortingsvarsel {
        val id: UUID
        val sakId: UUID
        val revurderingId: UUID
        val opprettet: Tidspunkt
        val simulering: Simulering

        fun hentUtbetalteBeløp(): Månedsbeløp {
            return simulering.hentUtbetalteBeløp()
        }

        data class Opprettet(
            override val id: UUID,
            override val sakId: UUID,
            override val revurderingId: UUID,
            override val opprettet: Tidspunkt,
            override val simulering: Simulering,
        ) : Utenlandsopphold {
            constructor(
                sakId: UUID,
                revurderingId: UUID,
                simulering: Simulering,
            ) : this(
                id = UUID.randomUUID(),
                sakId = sakId,
                revurderingId = revurderingId,
                opprettet = Tidspunkt.now(),
                simulering = simulering,
            )

            fun skalAvkortes(): SkalAvkortes {
                return SkalAvkortes(this)
            }
        }

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

        data class Avkortet(
            private val objekt: SkalAvkortes,
            val behandlingId: UUID,
        ) : Utenlandsopphold by objekt

        data class Annullert(
            private val objekt: SkalAvkortes,
            val behandlingId: UUID,
        ) : Utenlandsopphold by objekt
    }

    object Ingen : Avkortingsvarsel
}
