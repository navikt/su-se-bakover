package nøkkeltall.domain

import no.nav.su.se.bakover.common.domain.sak.Sakstype

interface NøkkeltallRepo {
    fun hentNøkkeltallForSakstype(sakstype: Sakstype): Nøkkeltall
}
