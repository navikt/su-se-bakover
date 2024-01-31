package no.nav.su.se.bakover.dokument.infrastructure.database

import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelseFilPostgresRepo
import java.time.Clock

class DokumentRepos(
    val clock: Clock,
    val sessionFactory: SessionFactory,
    val hendelseRepo: HendelseRepo,
    val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    val dokumentHendelseRepo: DokumentHendelseRepo = no.nav.su.se.bakover.dokument.infrastructure.database.DokumentHendelsePostgresRepo(
        hendelseRepo = hendelseRepo,
        hendelseFilRepo = HendelseFilPostgresRepo(sessionFactory),
        sessionFactory,
    ),
)
