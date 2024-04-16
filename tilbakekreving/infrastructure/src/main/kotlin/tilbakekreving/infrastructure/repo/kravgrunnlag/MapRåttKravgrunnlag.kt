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

/**
 * Historisk.
 * Brukes for å hente ut/visning av oversendte vedtak fra tilbakekrevinger under revurderinger.
 * */
typealias MapRåttKravgrunnlag = (råttKravgrunnlag: RåttKravgrunnlag) -> Either<Throwable, Kravgrunnlag>
typealias MapRåttKravgrunnlagTilHendelse = (
    råttKravgrunnlag: RåttKravgrunnlagHendelse,
    metaTilHendelsen: JMSHendelseMetadata,
    hentSak: (Saksnummer) -> Either<Throwable, Sak>,
    clock: Clock,
) -> Either<Throwable, Pair<Sak, KravgrunnlagPåSakHendelse>>
