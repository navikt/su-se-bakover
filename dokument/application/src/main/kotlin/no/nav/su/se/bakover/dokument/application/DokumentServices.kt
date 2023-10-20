package no.nav.su.se.bakover.dokument.application

import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.dokument.application.consumer.JournalførDokumentHendelserKonsument
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import person.domain.PersonService
import java.time.Clock

class DokumentServices(
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
    private val personService: PersonService,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val sakService: SakService,
    private val hendelseRepo: HendelseRepo,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
    private val dokArkiv: DokArkiv,
    val journalførtDokumentHendelserKonsument: JournalførDokumentHendelserKonsument = JournalførDokumentHendelserKonsument(
        sakService = sakService,
        personService = personService,
        dokArkiv = dokArkiv,
        dokumentHendelseRepo = dokumentHendelseRepo,
        hendelsekonsumenterRepo = hendelsekonsumenterRepo,
        hendelseRepo = hendelseRepo,
        sessionFactory = sessionFactory,
        clock = clock,
    ),
)
