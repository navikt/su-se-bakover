package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

interface UføregrunnlagRepo {
    fun hentUføregrunnlag(behandlingId: UUID): List<Grunnlag.Uføregrunnlag>
    fun hentForUføregrunnlagId(uføregrunnlagId: UUID): Grunnlag.Uføregrunnlag?
}
