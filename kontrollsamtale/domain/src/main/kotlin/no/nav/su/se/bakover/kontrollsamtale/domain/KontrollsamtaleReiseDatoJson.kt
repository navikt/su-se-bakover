package no.nav.su.se.bakover.kontrollsamtale.domain

import no.nav.su.se.bakover.common.serialize

private data class KontrollsamtaleReiseDatoJson(
    val utreiseDato: String,
    val innreiseDato: String,
)

private fun KontrollsamtaleReiseDato.toJson() =
    KontrollsamtaleReiseDatoJson(
        utreiseDato = utreiseDato.toString(),
        innreiseDato = innreiseDato.toString(),
    )
fun List<KontrollsamtaleReiseDato>.toDatabaseJson(): String = this.map { it.toJson() }.serialize()
