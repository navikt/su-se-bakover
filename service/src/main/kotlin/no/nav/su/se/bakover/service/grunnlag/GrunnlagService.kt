package no.nav.su.se.bakover.service.grunnlag

import no.nav.su.se.bakover.database.grunnlag.GrunnlagRepo
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

interface GrunnlagService {
    fun lagreFradragsgrunnlag(behandlingId: UUID, fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>)

    fun lagreBosituasjongrunnlag(behandlingId: UUID, bosituasjongrunnlag: List<Grunnlag.Bosituasjon>)

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

    override fun lagreBosituasjongrunnlag(behandlingId: UUID, bosituasjongrunnlag: List<Grunnlag.Bosituasjon>) {
        return grunnlagRepo.lagreBosituasjongrunnlag(behandlingId, bosituasjongrunnlag)
    }
}
