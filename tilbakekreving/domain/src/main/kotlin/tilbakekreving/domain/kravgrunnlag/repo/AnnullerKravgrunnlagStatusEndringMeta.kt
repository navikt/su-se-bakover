package tilbakekreving.domain.kravgrunnlag.repo

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import tilbakekreving.domain.kravgrunnlag.rått.RåTilbakekrevingsvedtakForsendelse

data class AnnullerKravgrunnlagStatusEndringMeta(
    override val correlationId: CorrelationId?,
    override val ident: NavIdentBruker?,
    override val brukerroller: List<Brukerrolle>,
    val tilbakekrevingsvedtakForsendelse: RåTilbakekrevingsvedtakForsendelse,
) : HendelseMetadata
