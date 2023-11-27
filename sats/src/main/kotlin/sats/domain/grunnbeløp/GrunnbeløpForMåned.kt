package sats.domain.grunnbeløp

import no.nav.su.se.bakover.common.extensions.avrund
import no.nav.su.se.bakover.common.extensions.isEqualOrBefore
import no.nav.su.se.bakover.common.tid.periode.Måned
import java.math.BigDecimal
import java.time.LocalDate

data class GrunnbeløpForMåned(
    val måned: Måned,
    val grunnbeløpPerÅr: Int,
    val ikrafttredelse: LocalDate,
    val virkningstidspunkt: LocalDate,
) {
    init {
        require(grunnbeløpPerÅr >= 0)
        require(virkningstidspunkt.isEqualOrBefore(måned.fraOgMed)) {
            "virkningstidspunkt: $virkningstidspunkt må være lik eller før ${måned.fraOgMed}"
        }
    }

    /**
     * TODO("håndter_formue egentlig knyttet til formuegrenser")
     * Dagens formuegrense er definert til et halvt grunnbeløp og brukes blant annet av brev. På sikt bør denne hentes ut av Formuegrunnlag i stedet.
     *
     * DEPRECATED: Denne er knyttet til formuegrenser og bør heller hentes fra:
     * - [no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory]
     * - [no.nav.su.se.bakover.domain.vilkår.FormuegrenseForMåned]
     */
    fun halvtGrunnbeløpPerÅrAvrundet(): Int = BigDecimal(grunnbeløpPerÅr).divide(BigDecimal(2)).avrund()
}
