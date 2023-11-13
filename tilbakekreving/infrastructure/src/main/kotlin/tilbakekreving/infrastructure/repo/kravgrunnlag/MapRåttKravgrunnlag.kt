package tilbakekreving.infrastructure.repo.kravgrunnlag

import arrow.core.Either
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sak
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlagHendelse
import java.time.Clock

/** DEPRECATED: Skal kun brukes i forbindelse med historiske rå kravgrunnlag på tilbakekrevinger under revurderinger. Disse forholdt seg aldri til statusendringene. */
typealias MapRåttKravgrunnlag = (råttKravgrunnlag: RåttKravgrunnlag) -> Either<Throwable, Kravgrunnlag>
typealias MapRåttKravgrunnlagTilHendelse = (
    råttKravgrunnlag: RåttKravgrunnlagHendelse,
    correlationId: CorrelationId,
    hentSak: (Saksnummer) -> Either<Throwable, Sak>,
    clock: Clock,
) -> Either<Throwable, Pair<Sak, KravgrunnlagPåSakHendelse>>
