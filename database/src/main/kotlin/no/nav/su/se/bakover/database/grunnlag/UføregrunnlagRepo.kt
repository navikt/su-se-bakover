package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

interface UføregrunnlagRepo {
    fun lagre(behandlingId: UUID, uføregrunnlag: List<Grunnlag.Uføregrunnlag>)
    fun hent(behandlingId: UUID): List<Grunnlag.Uføregrunnlag>
    fun hentForUføregrunnlagId(uføregrunnlagId: UUID): Grunnlag.Uføregrunnlag?
    fun slett(uføregrunnlagId: UUID)
}
