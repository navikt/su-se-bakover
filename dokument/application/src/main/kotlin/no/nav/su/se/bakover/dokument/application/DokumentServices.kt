package no.nav.su.se.bakover.dokument.application

import dokument.domain.distribuering.DokDistFordeling
import dokument.domain.hendelser.DokumentHendelseRepo
import dokument.domain.journalføring.brev.JournalførBrevClient
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.dokument.application.consumer.DistribuerDokumentHendelserKonsument
import no.nav.su.se.bakover.dokument.application.consumer.JournalførDokumentHendelserKonsument
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import person.domain.PersonService
import java.time.Clock

class DokumentServices(
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
    private val personService: PersonService,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val sakService: SakService,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
    private val journalførBrevClient: JournalførBrevClient,
    private val dokDistFordeling: DokDistFordeling,
    val journalførtDokumentHendelserKonsument: JournalførDokumentHendelserKonsument = JournalførDokumentHendelserKonsument(
        sakService = sakService,
        personService = personService,
        journalførBrevClient = journalførBrevClient,
        dokumentHendelseRepo = dokumentHendelseRepo,
        hendelsekonsumenterRepo = hendelsekonsumenterRepo,
        sessionFactory = sessionFactory,
        clock = clock,
    ),
    val distribuerDokumentHendelserKonsument: DistribuerDokumentHendelserKonsument = DistribuerDokumentHendelserKonsument(
        sakService = sakService,
        dokDistFordeling = dokDistFordeling,
        hendelsekonsumenterRepo = hendelsekonsumenterRepo,
        dokumentHendelseRepo = dokumentHendelseRepo,
        sessionFactory = sessionFactory,
        clock = clock,
    ),
)
