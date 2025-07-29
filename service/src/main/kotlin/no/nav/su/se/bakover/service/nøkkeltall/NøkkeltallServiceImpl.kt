package no.nav.su.se.bakover.service.nøkkeltall

import no.nav.su.se.bakover.common.domain.sak.Sakstype
import nøkkeltall.domain.NøkkeltallPerSakstype
import nøkkeltall.domain.NøkkeltallRepo

class NøkkeltallServiceImpl(private val nøkkeltallRepo: NøkkeltallRepo) : NøkkeltallService {
    override fun hentNøkkeltall(): List<NøkkeltallPerSakstype> {
        return Sakstype.entries.map { sakstype ->
            NøkkeltallPerSakstype(sakstype, nøkkeltallRepo.hentNøkkeltall(sakstype))
        }
    }
}
