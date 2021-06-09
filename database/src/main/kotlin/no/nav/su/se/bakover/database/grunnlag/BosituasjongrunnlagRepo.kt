package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

interface BosituasjongrunnlagRepo {
    fun lagreBosituasjongrunnlag(behandlingId: UUID, grunnlag: List<Grunnlag.Bosituasjon>)
    fun hentBosituasjongrunnlag(behandlingId: UUID): List<Grunnlag.Bosituasjon>
}
