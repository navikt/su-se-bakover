package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import java.util.UUID

interface FormuesgrunnlagRepo {
    fun lagreFormuesgrunnlag(behandlingId: UUID, formuesgrunnlag: List<Formuegrunnlag>)
    fun hentFormuesgrunnlag(behandlingId: UUID): List<Formuegrunnlag>
}
