package no.nav.su.se.bakover.domain.tidslinje

import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.Copyable
import no.nav.su.se.bakover.domain.OriginaltTidsstempel

interface KanPlasseresPåTidslinje<Type> :
    OriginaltTidsstempel,
    PeriodisertInformasjon,
    Copyable<CopyArgs.Tidslinje, Type>
