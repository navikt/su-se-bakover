package tilbakekreving.domain.kravgrunnlag.repo

import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.persistence.SessionContext

interface BehandlingssammendragKravgrunnlagRepo {
    fun hentBehandlingssammendrag(
        sessionContext: SessionContext?,
    ): List<Behandlingssammendrag>
}
