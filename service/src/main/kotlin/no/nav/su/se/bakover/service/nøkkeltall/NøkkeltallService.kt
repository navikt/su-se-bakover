package no.nav.su.se.bakover.service.nøkkeltall

import nøkkeltall.domain.Nøkkeltall

interface NøkkeltallService {
    fun hentNøkkeltall(): Nøkkeltall
}
