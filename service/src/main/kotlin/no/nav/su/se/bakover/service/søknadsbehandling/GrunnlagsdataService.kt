package no.nav.su.se.bakover.service.søknadsbehandling

import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata.BehandlingUføregrunnlag
import java.util.UUID

interface GrunnlagsdataService {
    fun leggTilUførerunnlag(sakId: UUID, behandlingId: UUID, uføregrunnlag: BehandlingUføregrunnlag)
}

class GrunnlagsdataServiceImpl : GrunnlagsdataService {
    override fun leggTilUførerunnlag(sakId: UUID, behandlingId: UUID, uføregrunnlag: BehandlingUføregrunnlag) {
        // TODO: Accept a list. Return an Either. insert into table(s). Call søknadbehandlingsservice. Make frontend call this endpoint instead of the patch one.
    }
}
