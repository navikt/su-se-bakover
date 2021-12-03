package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.time.LocalDate
import java.util.UUID

sealed class Feilutbetalingsvarsel {

    data class Feilutbetalingslinje(
        val fraOgMed: LocalDate,
        val tilOgMed: LocalDate,
        var forrigeUtbetalingslinjeId: UUID30?,
        val beløp: Int,
        val virkningstidspunkt: LocalDate,
        val uføregrad: Uføregrad?,
    )

    data class KanAvkortes(
        val id: UUID,
        val opprettet: Tidspunkt,
        val simulering: Simulering,
        val feilutbetalingslinje: Feilutbetalingslinje,
    ) : Feilutbetalingsvarsel() {
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
    }

    object MåTilbakekreves : Feilutbetalingsvarsel()
    object Ingen : Feilutbetalingsvarsel()
}
