package no.nav.su.se.bakover.kontrollsamtale.infrastructure.setup

import dokument.domain.brev.BrevService
import dokument.domain.journalføring.QueryJournalpostClient
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.database.jobcontext.JobContextPostgresRepo
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.revurdering.opphør.AnnullerKontrollsamtaleVedOpphørService
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.OpprettKontrollsamtaleVedNyStønadsperiodeService
import no.nav.su.se.bakover.kontrollsamtale.application.KontrollsamtaleServiceImpl
import no.nav.su.se.bakover.kontrollsamtale.application.annuller.AnnullerKontrollsamtaleVedOpphørServiceImpl
import no.nav.su.se.bakover.kontrollsamtale.application.opprett.OpprettPlanlagtKontrollsamtaleServiceImpl
import no.nav.su.se.bakover.kontrollsamtale.application.utløptfrist.UtløptFristForKontrollsamtaleServiceImpl
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.UtløptFristForKontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.persistence.KontrollsamtaleJobPostgresRepo
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.persistence.KontrollsamtalePostgresRepo
import person.domain.PersonService
import java.time.Clock

interface KontrollsamtaleSetup {
    val kontrollsamtaleService: KontrollsamtaleService
    val annullerKontrollsamtaleService: AnnullerKontrollsamtaleVedOpphørService
    val opprettPlanlagtKontrollsamtaleService: OpprettKontrollsamtaleVedNyStønadsperiodeService
    val utløptFristForKontrollsamtaleService: UtløptFristForKontrollsamtaleService

    companion object {
        fun create(
            sakService: SakService,
            brevService: BrevService,
            oppgaveService: OppgaveService,
            sessionFactory: PostgresSessionFactory,
            dbMetrics: DbMetrics,
            clock: Clock,
            serviceUser: String,
            jobContextPostgresRepo: JobContextPostgresRepo,
            queryJournalpostClient: QueryJournalpostClient,
            stansAvYtelseService: StansYtelseService,
            personService: PersonService,
        ): KontrollsamtaleSetup {
            val kontrollsamtaleRepo = KontrollsamtalePostgresRepo(
                sessionFactory = sessionFactory,
                dbMetrics = dbMetrics,
            )
            val kontrollsamtaleJobRepo = KontrollsamtaleJobPostgresRepo(jobContextPostgresRepo)
            val kontrollsamtaleService = KontrollsamtaleServiceImpl(
                sakService = sakService,
                brevService = brevService,
                oppgaveService = oppgaveService,
                kontrollsamtaleRepo = kontrollsamtaleRepo,
                sessionFactory = sessionFactory,
                clock = clock,
                personService = personService,
                queryJournalpostClient = queryJournalpostClient,
            )
            return object : KontrollsamtaleSetup {
                override val kontrollsamtaleService = kontrollsamtaleService
                override val annullerKontrollsamtaleService = AnnullerKontrollsamtaleVedOpphørServiceImpl(
                    kontrollsamtaleService = kontrollsamtaleService,
                    kontrollsamtaleRepo = kontrollsamtaleRepo,
                )
                override val opprettPlanlagtKontrollsamtaleService = OpprettPlanlagtKontrollsamtaleServiceImpl(
                    kontrollsamtaleService = kontrollsamtaleService,
                    kontrollsamtaleRepo = kontrollsamtaleRepo,
                    clock = clock,
                )
                override val utløptFristForKontrollsamtaleService = UtløptFristForKontrollsamtaleServiceImpl(
                    sakService = sakService,
                    queryJournalpostClient = queryJournalpostClient,
                    kontrollsamtaleService = kontrollsamtaleService,
                    stansAvYtelseService = stansAvYtelseService,
                    sessionFactory = sessionFactory,
                    clock = clock,
                    serviceUser = serviceUser,
                    oppgaveService = oppgaveService,
                    kontrollsamtaleJobRepo = kontrollsamtaleJobRepo,
                    kontrollsamtaleRepo = kontrollsamtaleRepo,
                    personService = personService,
                )
            }
        }
    }
}
