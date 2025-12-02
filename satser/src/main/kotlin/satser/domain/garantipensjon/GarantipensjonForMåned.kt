package satser.domain.garantipensjon

import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertBeregning
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertGrunnlag
import no.nav.su.se.bakover.common.tid.periode.Måned
import satser.domain.Satskategori
import java.time.LocalDate

data class GarantipensjonForMåned(
    val måned: Måned,
    val satsKategori: Satskategori,
    val garantipensjonPerÅr: Int,
    /** Datoen loven trådte i kraft; ofte rundt 20. mai, men det kan variere. */
    val ikrafttredelse: LocalDate,
    /** Datoen denne garantipensjonen gjelder fra (med tilbakevirkende kraft); typisk 1 mai. */
    val virkningstidspunkt: LocalDate,
    override val benyttetRegel: Regelspesifisering = when (satsKategori) {
        Satskategori.ORDINÆR -> RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_ORDINÆR.benyttGrunnlag(
            garantipensjonPerÅr.toString(),
        )
        Satskategori.HØY -> RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_HØY.benyttGrunnlag(
            garantipensjonPerÅr.toString(),
        )
    },
) : RegelspesifisertBeregning
