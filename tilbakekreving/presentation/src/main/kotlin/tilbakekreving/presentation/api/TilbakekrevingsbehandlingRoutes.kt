package tilbakekreving.presentation.api

import io.ktor.server.routing.Route
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.domain.person.PersonRepo
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import tilbakekreving.application.service.BrevTilbakekrevingsbehandlingService
import tilbakekreving.application.service.HentÅpentKravgrunnlagService
import tilbakekreving.application.service.MånedsvurderingerTilbakekrevingsbehandlingService
import tilbakekreving.application.service.OpprettTilbakekrevingsbehandlingService
import tilbakekreving.application.service.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.application.service.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingService
import tilbakekreving.infrastructure.KravgrunnlagPostgresRepo
import tilbakekreving.infrastructure.TilbakekrevingsbehandlingPostgresRepo
import tilbakekreving.presentation.api.forhåndsvarsel.forhåndsvarsleTilbakekrevingRoute
import tilbakekreving.presentation.api.hent.hentKravgrunnlagRoute
import tilbakekreving.presentation.api.opprett.opprettTilbakekrevingsbehandlingRoute
import tilbakekreving.presentation.api.vurder.brevTilbakekrevingsbehandlingRoute
import tilbakekreving.presentation.api.vurder.månedsvurderingerTilbakekrevingsbehandlingRoute
import tilbakekreving.presentation.consumer.TilbakekrevingsmeldingMapper
import java.time.Clock

internal const val tilbakekrevingPath = "saker/{sakId}/tilbakekreving"

fun Route.tilbakekrevingRoutes(
    personRepo: PersonRepo,
    hendelseRepo: HendelseRepo,
    hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    personService: PersonService,
    sakService: SakService,
    sessionFactory: PostgresSessionFactory,
    clock: Clock,
) {
    val tilgangstyringService = TilbakekrevingsbehandlingTilgangstyringService(
        personRepo = personRepo,
        personService = personService,
    )
    val kravgrunnlagRepo = KravgrunnlagPostgresRepo(
        sessionFactory = sessionFactory,
        mapper = TilbakekrevingsmeldingMapper::toKravgrunnlag,
        hendelseRepo = hendelseRepo,
        hendelsekonsumenterRepo = hendelsekonsumenterRepo,
    )
    val tilbakekrevingsbehHandlingRepo = TilbakekrevingsbehandlingPostgresRepo(
        sessionFactory = sessionFactory,
        hendelseRepo = hendelseRepo,
        clock = clock,
        kravgrunnlagRepo = kravgrunnlagRepo,
    )

    this.hentKravgrunnlagRoute(
        HentÅpentKravgrunnlagService(
            kravgrunnlagRepo = kravgrunnlagRepo,
            tilgangstyring = tilgangstyringService,
        ),
    )
    this.opprettTilbakekrevingsbehandlingRoute(
        OpprettTilbakekrevingsbehandlingService(
            kravgrunnlagRepo = kravgrunnlagRepo,
            tilbakekrevingsbehandlingRepo = tilbakekrevingsbehHandlingRepo,
            tilgangstyring = tilgangstyringService,
            hendelseRepo = hendelseRepo,
            clock = clock,
        ),
    )

    this.månedsvurderingerTilbakekrevingsbehandlingRoute(
        MånedsvurderingerTilbakekrevingsbehandlingService(
            tilbakekrevingsbehandlingRepo = tilbakekrevingsbehHandlingRepo,
            sakService = sakService,
            tilgangstyring = tilgangstyringService,
            hendelseRepo = hendelseRepo,
            clock = clock,
        ),
    )

    this.brevTilbakekrevingsbehandlingRoute(
        BrevTilbakekrevingsbehandlingService(
            tilgangstyring = tilgangstyringService,
            sakService = sakService,
            tilbakekrevingsbehandlingRepo = tilbakekrevingsbehHandlingRepo,
            clock = clock,
        ),
    )

    this.forhåndsvarsleTilbakekrevingRoute(
        ForhåndsvarsleTilbakekrevingsbehandlingService(
            tilbakekrevingsbehandlingRepo = tilbakekrevingsbehHandlingRepo,
            sakService = sakService,
            tilgangstyring = tilgangstyringService,
        ),
    )
}
