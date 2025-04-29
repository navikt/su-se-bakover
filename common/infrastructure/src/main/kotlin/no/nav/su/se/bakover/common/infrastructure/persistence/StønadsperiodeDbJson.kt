package no.nav.su.se.bakover.common.infrastructure.persistence

import no.nav.su.se.bakover.common.domain.Stønadsperiode

/** Skal kun brukes i serialisering/deserialisering mot databasen. **/
data class StønadsperiodeDbJson(
    val periode: PeriodeDbJson,
)

/** Skal kun brukes i serialisering/deserialisering mot databasen. **/
fun Stønadsperiode.toDbJson(): StønadsperiodeDbJson {
    return StønadsperiodeDbJson(
        periode = this.periode.toDbJson(),
    )
}

/** Skal kun brukes i serialisering/deserialisering mot databasen. **/
fun StønadsperiodeDbJson.toDomain(): Stønadsperiode {
    return Stønadsperiode.create(
        periode = this.periode.toDomain(),
    )
}
