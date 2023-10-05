package tilbakekreving.infrastructure

import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import java.time.Clock

/**
 * Et forsøk på modularisering av [no.nav.su.se.bakover.domain.DatabaseRepos] der de forskjellige modulene er ansvarlige for å wire opp sine komponenter.
 *
 * Det kan hende vi må splitte denne i en data class + builder.
 */
class TilbakekrevingRepos(
    val clock: Clock,
    val sessionFactory: SessionFactory,
    val hendelseRepo: HendelseRepo,
    val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    val kravgrunnlagRepo: KravgrunnlagRepo = KravgrunnlagPostgresRepo(
        hendelseRepo = hendelseRepo,
        hendelsekonsumenterRepo = hendelsekonsumenterRepo,
    ),
    val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo = TilbakekrevingsbehandlingPostgresRepo(
        sessionFactory = sessionFactory,
        hendelseRepo = hendelseRepo,
        clock = clock,
        kravgrunnlagRepo = kravgrunnlagRepo,
    ),
)
