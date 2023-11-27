package sats

import no.nav.su.se.bakover.common.tid.periode.Måned
import java.time.LocalDate

data class GarantipensjonForMåned(
    val måned: Måned,
    val satsKategori: Satskategori,
    val garantipensjonPerÅr: Int,
    /** Datoen loven trådte i kraft; ofte rundt 20. mai, men det kan variere. */
    val ikrafttredelse: LocalDate,
    /** Datoen denne garantipensjonen gjelder fra (med tilbakevirkende kraft); typisk 1 mai. */
    val virkningstidspunkt: LocalDate,
)
