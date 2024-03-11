package no.nav.su.se.bakover.service.nøkkeltall

import nøkkeltall.domain.Nøkkeltall
import nøkkeltall.domain.NøkkeltallRepo

class NøkkeltallServiceImpl(private val nøkkeltallRepo: NøkkeltallRepo) : NøkkeltallService {
    override fun hentNøkkeltall(): Nøkkeltall {
        return nøkkeltallRepo.hentNøkkeltall()
    }
}
