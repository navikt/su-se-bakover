package no.nav.su.se.bakover.database.nøkkeltall

import no.nav.su.se.bakover.domain.nøkkeltall.Nøkkeltall

interface NøkkeltallRepo {
    fun hentNøkkeltall(): Nøkkeltall?
}
