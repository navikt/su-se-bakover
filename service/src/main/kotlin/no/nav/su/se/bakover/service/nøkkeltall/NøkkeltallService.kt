package no.nav.su.se.bakover.service.nøkkeltall

import no.nav.su.se.bakover.domain.nøkkeltall.Nøkkeltall

interface NøkkeltallService {
    fun hentNøkkeltall(): Nøkkeltall
}
