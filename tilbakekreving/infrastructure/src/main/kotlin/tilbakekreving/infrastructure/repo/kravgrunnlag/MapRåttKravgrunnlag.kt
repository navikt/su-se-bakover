package tilbakekreving.infrastructure.repo.kravgrunnlag

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.rått.RåttKravgrunnlag
import tilbakekreving.domain.kravgrunnlag.rått.RåttKravgrunnlagHendelse
import java.time.Clock

/** DEPRECATED: Skal kun brukes i forbindelse med historiske rå kravgrunnlag på tilbakekrevinger under revurderinger. Disse forholdt seg aldri til statusendringene. */
typealias MapRåttKravgrunnlag = (råttKravgrunnlag: RåttKravgrunnlag) -> Either<Throwable, Kravgrunnlag>
typealias MapRåttKravgrunnlagTilHendelse = (
    råttKravgrunnlag: RåttKravgrunnlagHendelse,
    metaTilHendelsen: JMSHendelseMetadata,
    hentSak: (Saksnummer) -> Either<Throwable, Sak>,
    clock: Clock,
) -> Either<Throwable, Pair<Sak, KravgrunnlagPåSakHendelse>>
