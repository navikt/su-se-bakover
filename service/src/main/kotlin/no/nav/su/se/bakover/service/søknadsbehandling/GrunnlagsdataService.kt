package no.nav.su.se.bakover.service.søknadsbehandling

import no.nav.su.se.bakover.database.grunnlag.GrunnlagRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata.Uføregrunnlag
import java.util.UUID

interface GrunnlagsdataService {
    fun leggTilUførerunnlag(sakId: UUID, behandlingId: UUID, uføregrunnlag: List<Uføregrunnlag>)
}

internal class GrunnlagsdataServiceImpl(
    private val grunnlagRepo: GrunnlagRepo
) : GrunnlagsdataService {
    override fun leggTilUførerunnlag(sakId: UUID, behandlingId: UUID, uføregrunnlag: List<Uføregrunnlag>) {
        // TODO: Accept a list. Return an Either. insert into table(s). Call søknadbehandlingsservice. Make frontend call this endpoint instead of the patch one.
        grunnlagRepo.lagre(behandlingId, uføregrunnlag)
    }
}
