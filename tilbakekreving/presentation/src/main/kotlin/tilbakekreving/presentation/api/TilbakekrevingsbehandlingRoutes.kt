package tilbakekreving.presentation.api

import io.ktor.server.routing.Route
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.domain.person.PersonRepo
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import tilbakekreving.application.service.HentÅpentKravgrunnlagService
import tilbakekreving.application.service.OpprettTilbakekrevingsbehandlingService
import tilbakekreving.application.service.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.infrastructure.KravgrunnlagPostgresRepo
import tilbakekreving.infrastructure.TilbakekrevingsbehandlingPostgresRepo
import tilbakekreving.presentation.api.hent.hentKravgrunnlagRoute
import tilbakekreving.presentation.api.opprett.opprettTilbakekrevingsbehandlingRoute
import tilbakekreving.presentation.consumer.TilbakekrevingsmeldingMapper
import java.time.Clock

internal const val tilbakekrevingPath = "saker/{sakId}/tilbakekreving"

fun Route.tilbakekrevingRoutes(
    personRepo: PersonRepo,
    hendelseRepo: HendelseRepo,
    personService: PersonService,
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
    )
    val tilbakekrevingsbehHandlingRepo = TilbakekrevingsbehandlingPostgresRepo(
        sessionFactory = sessionFactory,
        hendelseRepo = hendelseRepo,
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
}
