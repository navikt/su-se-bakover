package no.nav.su.se.bakover.domain.skatt

import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.common.tid.krympTilØvreGrense
import no.nav.su.se.bakover.common.tid.utvidNedreGrense
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import java.time.Clock
import java.time.Year

/**
 * @return Skatteperiode for stønadsperiode. Hvis stønadsperiode er null, returneres skatteperiode for de tre siste årene.
 */
fun Stønadsperiode?.regnUtSkatteperiodeForStønadsperiode(clock: Clock): YearRange {
    val iÅr = Year.now(clock)
    val iForFjor = iÅr.minusYears(2)
    if (this == null) {
        return YearRange(iForFjor, iÅr)
    }

    return this
        .toYearRange()
        .krympTilØvreGrense(iÅr)
        .utvidNedreGrense(iForFjor)
}
