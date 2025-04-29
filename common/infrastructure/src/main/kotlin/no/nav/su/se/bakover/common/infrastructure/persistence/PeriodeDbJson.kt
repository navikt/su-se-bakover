package no.nav.su.se.bakover.common.infrastructure.persistence

import no.nav.su.se.bakover.common.tid.periode.Periode
import java.time.LocalDate

/** Skal kun brukes i serialisering/deserialisering mot databasen. **/
data class PeriodeDbJson(
    val fraOgMed: String,
    val tilOgMed: String,
)

/** Skal kun brukes i serialisering/deserialisering mot databasen. **/
fun PeriodeDbJson.toDomain(): Periode {
    return Periode.create(
        fraOgMed = LocalDate.parse(this.fraOgMed),
        tilOgMed = LocalDate.parse(this.tilOgMed),
    )
}

/** Skal kun brukes i serialisering/deserialisering mot databasen. **/
fun Periode.toDbJson(): PeriodeDbJson {
    return PeriodeDbJson(
        fraOgMed = this.fraOgMed.toString(),
        tilOgMed = this.tilOgMed.toString(),
    )
}
