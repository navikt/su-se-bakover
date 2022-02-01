package no.nav.su.se.bakover.domain.grunnlag

import java.util.UUID

interface FradragsgrunnlagRepo {
    fun lagreFradragsgrunnlag(behandlingId: UUID, fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>)
}
