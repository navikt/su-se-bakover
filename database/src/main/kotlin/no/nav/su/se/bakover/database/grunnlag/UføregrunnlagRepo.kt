package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata.Uføregrunnlag
import java.util.UUID

interface UføregrunnlagRepo {
    fun lagre(behandlingId: UUID, uføregrunnlag: List<Uføregrunnlag>)
    fun hent(behandlingId: UUID): List<Uføregrunnlag>
    fun slett(uføregrunnlagId: UUID)
}
