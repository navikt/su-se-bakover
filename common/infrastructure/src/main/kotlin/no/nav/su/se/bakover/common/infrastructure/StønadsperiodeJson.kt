package no.nav.su.se.bakover.common.infrastructure

import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson

/** Ment brukt i web-laget (ikke db). For db, se: [no.nav.su.se.bakover.common.infrastructure.persistence.StønadsperiodeDbJson] */
data class StønadsperiodeJson(val periode: PeriodeJson)

/** Ment brukt i web-laget (ikke db). For db, se: [no.nav.su.se.bakover.common.infrastructure.persistence.StønadsperiodeDbJson] */
fun Stønadsperiode.toJson(): StønadsperiodeJson {
    return StønadsperiodeJson(periode.toJson())
}

/** Ment brukt i web-laget (ikke db). For db, se: [no.nav.su.se.bakover.common.infrastructure.persistence.StønadsperiodeDbJson] */
fun StønadsperiodeJson.toDomain(): Stønadsperiode {
    return Stønadsperiode.create(periode = periode.toPeriode())
}
