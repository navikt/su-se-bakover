package tilbakekreving.domain.kravgrunnlag

import java.util.UUID

interface KravgrunnlagRepo {
    fun hentÅpentKravgrunnlagForSak(sakId: UUID): RåttKravgrunnlag?
}
