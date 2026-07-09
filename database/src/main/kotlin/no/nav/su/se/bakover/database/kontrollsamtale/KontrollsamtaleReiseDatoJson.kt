package no.nav.su.se.bakover.database.kontrollsamtale

import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleReiseDato
import java.time.LocalDate

internal data class KontrollsamtaleReiseDatoJson(
    val utreiseDato: String,
    val innreiseDato: String,
) {
    fun toDomain() = KontrollsamtaleReiseDato(
        utreiseDato = LocalDate.parse(utreiseDato),
        innreiseDato = LocalDate.parse(innreiseDato),
    )
}

private fun KontrollsamtaleReiseDato.toJson() =
    KontrollsamtaleReiseDatoJson(
        utreiseDato = utreiseDato.toString(),
        innreiseDato = innreiseDato.toString(),
    )
fun List<KontrollsamtaleReiseDato>.toDatabaseJson(): String = this.map { it.toJson() }.serialize()
fun String.toKontrollsamtaleReiseDatoList(): List<KontrollsamtaleReiseDato> =
    this.deserializeList<KontrollsamtaleReiseDatoJson>().map { it.toDomain() }
