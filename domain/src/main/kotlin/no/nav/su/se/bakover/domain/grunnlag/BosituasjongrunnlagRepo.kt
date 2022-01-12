package no.nav.su.se.bakover.domain.grunnlag

import java.util.UUID

interface BosituasjongrunnlagRepo {
    fun lagreBosituasjongrunnlag(behandlingId: UUID, grunnlag: List<Grunnlag.Bosituasjon>)
    fun hentBosituasjongrunnlag(behandlingId: UUID): List<Grunnlag.Bosituasjon>
}
