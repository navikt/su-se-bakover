package no.nav.su.se.bakover.service.nøkkeltall

import nøkkeltall.domain.NøkkeltallPerSakstype

interface NøkkeltallService {
    fun hentNøkkeltall(): List<NøkkeltallPerSakstype>
}
