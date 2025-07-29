package nøkkeltall.domain

import no.nav.su.se.bakover.common.domain.sak.Sakstype

interface NøkkeltallRepo {
    fun hentNøkkeltall(sakstype: Sakstype): Nøkkeltall
}
