package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata.BehandlingUføregrunnlag
import java.util.UUID

interface UføregrunnlagRepo {
    fun lagre(behandlingId: UUID, uføregrunnlag: List<BehandlingUføregrunnlag>)
    fun hent(behandlingId: UUID): List<BehandlingUføregrunnlag>
    fun slett(uføregrunnlagId: UUID)
}
