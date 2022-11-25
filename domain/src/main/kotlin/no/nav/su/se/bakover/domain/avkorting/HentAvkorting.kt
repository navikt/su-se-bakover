package no.nav.su.se.bakover.domain.avkorting

import no.nav.su.se.bakover.domain.Sak
import java.util.UUID

/**
 * Saken inneholder 1 eller 0 avkortinger.
 * Dvs. enten [Avkortingsvarsel.Utenlandsopphold.SkalAvkortes] eller [Avkortingsvarsel.Ingen]
 */
fun Sak.hentAvkorting(id: UUID): Avkortingsvarsel.Utenlandsopphold.SkalAvkortes? {
    return when (uteståendeAvkorting) {
        is Avkortingsvarsel.Ingen -> null
        is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes -> uteståendeAvkorting
        else -> throw IllegalStateException("Sak $id inneholder ikke-støttet avkortingstype ${uteståendeAvkorting::class}")
    }
}
