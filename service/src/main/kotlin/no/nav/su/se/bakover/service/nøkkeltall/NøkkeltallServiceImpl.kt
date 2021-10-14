package no.nav.su.se.bakover.service.nøkkeltall

import no.nav.su.se.bakover.domain.nøkkeltall.Nøkkeltall
import no.nav.su.se.bakover.domain.nøkkeltall.NøkkeltallRepo

class NøkkeltallServiceImpl(private val nøkkeltallRepo: NøkkeltallRepo) : NøkkeltallService {
    override fun hentNøkkeltall(): Nøkkeltall {
        return nøkkeltallRepo.hentNøkkeltall()
    }
}
