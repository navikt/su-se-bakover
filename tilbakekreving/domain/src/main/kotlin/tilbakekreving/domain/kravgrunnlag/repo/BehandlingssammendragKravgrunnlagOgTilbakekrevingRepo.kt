package tilbakekreving.domain.kravgrunnlag.repo

import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.persistence.SessionContext

interface BehandlingssammendragKravgrunnlagOgTilbakekrevingRepo {
    fun hentÅpne(sessionContext: SessionContext?): List<Behandlingssammendrag>
    fun hentFerdige(sessionContext: SessionContext?): List<Behandlingssammendrag>
}
