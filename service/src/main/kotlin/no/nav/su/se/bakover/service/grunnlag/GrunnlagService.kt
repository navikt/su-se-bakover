package no.nav.su.se.bakover.service.grunnlag

import no.nav.su.se.bakover.database.grunnlag.GrunnlagRepo
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

interface GrunnlagService {
    fun lagreFradragsgrunnlag(behandlingId: UUID, fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>)
    fun hentFradragsgrunnlag(behandlingId: UUID): List<Grunnlag.Fradragsgrunnlag>

    sealed class KunneIkkeLeggeTilGrunnlagsdata {
        object FantIkkeBehandling : KunneIkkeLeggeTilGrunnlagsdata()
        object UgyldigTilstand : KunneIkkeLeggeTilGrunnlagsdata()
    }
}

internal class GrunnlagServiceImpl(
    private val grunnlagRepo: GrunnlagRepo,
) : GrunnlagService {
    override fun lagreFradragsgrunnlag(behandlingId: UUID, fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) {
        return grunnlagRepo.lagreFradragsgrunnlag(behandlingId, fradragsgrunnlag)
    }

    override fun hentFradragsgrunnlag(behandlingId: UUID): List<Grunnlag.Fradragsgrunnlag> {
        return grunnlagRepo.hentFradragsgrunnlag(behandlingId)
    }
}
