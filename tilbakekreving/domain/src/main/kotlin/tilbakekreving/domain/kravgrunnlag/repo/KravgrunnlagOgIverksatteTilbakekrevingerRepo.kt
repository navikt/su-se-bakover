package tilbakekreving.domain.kravgrunnlag.repo

import no.nav.su.se.bakover.common.persistence.SessionContext
import tilbakekreving.domain.IverksattHendelse
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagPåSakHendelse

interface KravgrunnlagOgIverksatteTilbakekrevingerRepo {
    fun hentKravgrunnlagOgIverksatteTilbakekrevinger(
        sessionContext: SessionContext?,
    ): Pair<List<KravgrunnlagPåSakHendelse>, List<IverksattHendelse>>
}
