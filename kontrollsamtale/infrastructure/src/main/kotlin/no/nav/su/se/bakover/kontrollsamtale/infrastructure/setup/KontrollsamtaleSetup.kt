package no.nav.su.se.bakover.kontrollsamtale.infrastructure.setup

import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.database.jobcontext.JobContextPostgresRepo
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.PersonService
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
import java.time.Clock

interface KontrollsamtaleSetup {
    val kontrollsamtaleService: KontrollsamtaleService
    val annullerKontrollsamtaleService: AnnullerKontrollsamtaleVedOpphørService
    val opprettPlanlagtKontrollsamtaleService: OpprettKontrollsamtaleVedNyStønadsperiodeService
    val utløptFristForKontrollsamtaleService: UtløptFristForKontrollsamtaleService

    companion object {
        fun create(
            sakService: SakService,
            personService: PersonService,
            brevService: BrevService,
            oppgaveService: OppgaveService,
            sessionFactory: PostgresSessionFactory,
            dbMetrics: DbMetrics,
            clock: Clock,
            serviceUser: String,
            jobContextPostgresRepo: JobContextPostgresRepo,
            journalpostClient: JournalpostClient,
            stansAvYtelseService: StansYtelseService,
        ): KontrollsamtaleSetup {
            val kontrollsamtaleRepo = KontrollsamtalePostgresRepo(
                sessionFactory = sessionFactory,
                dbMetrics = dbMetrics,
            )
            val kontrollsamtaleJobRepo = KontrollsamtaleJobPostgresRepo(jobContextPostgresRepo)
            val kontrollsamtaleService = KontrollsamtaleServiceImpl(
                sakService = sakService,
                personService = personService,
                brevService = brevService,
                oppgaveService = oppgaveService,
                kontrollsamtaleRepo = kontrollsamtaleRepo,
                sessionFactory = sessionFactory,
                clock = clock,
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
                    journalpostClient = journalpostClient,
                    kontrollsamtaleService = kontrollsamtaleService,
                    stansAvYtelseService = stansAvYtelseService,
                    sessionFactory = sessionFactory,
                    clock = clock,
                    serviceUser = serviceUser,
                    oppgaveService = oppgaveService,
                    personService = personService,
                    kontrollsamtaleJobRepo = kontrollsamtaleJobRepo,
                )
            }
        }
    }
}
