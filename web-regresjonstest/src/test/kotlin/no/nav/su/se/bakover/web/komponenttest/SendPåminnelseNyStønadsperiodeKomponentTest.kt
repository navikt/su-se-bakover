package no.nav.su.se.bakover.web.komponenttest

import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.job.NameAndYearMonthId
import no.nav.su.se.bakover.common.domain.tid.august
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.database.jobcontext.JobContextPostgresRepo
import no.nav.su.se.bakover.database.stønadsperiode.SendPåminnelseNyStønadsperiodeJobPostgresRepo
import no.nav.su.se.bakover.domain.jobcontext.SendPåminnelseNyStønadsperiodeContext
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.sak.hent.hentSakId
import no.nav.su.se.bakover.web.sak.hent.hentSaksnummer
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Month
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

class SendPåminnelseNyStønadsperiodeKomponentTest {
    @Test
    fun `sender påminnelser for saker med utløp neste måned`() {
        val clock = Clock.fixed(2.juli(2022).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val jobbmåned = YearMonth.of(2022, Month.JULY)
        withKomptestApplication(
            clock = clock,
            clientsBuilder = { databaseRepos, klokke, _applicationConfig ->
                TestClientsBuilder(
                    clock = klokke,
                    databaseRepos = databaseRepos,
                    personOppslag = PersonOppslagStub(dødsdato = null),
                ).build(_applicationConfig)
            },
        ) { appComponents ->
            val sakMedUtløpFørJobbmåneden = opprettInnvilgetSøknadsbehandling(
                fnr = Fnr.generer().toString(),
                fraOgMed = 1.januar(2021).toString(),
                tilOgMed = 31.desember(2021).toString(),
                client = this.client,
                appComponents = appComponents,
            ).let {
                hentSak(BehandlingJson.hentSakId(it), client = this.client).let { sakJson ->
                    UUID.fromString(hentSakId(sakJson)) to Saksnummer(hentSaksnummer(sakJson).toLong())
                }
            }

            val sakMedUtløpMånedenEtterJobbmåneden = opprettInnvilgetSøknadsbehandling(
                fnr = Fnr.generer().toString(),
                fraOgMed = 1.januar(2022).toString(),
                tilOgMed = 31.august(2022).toString(),
                client = this.client,
                appComponents = appComponents,
            ).let {
                hentSak(BehandlingJson.hentSakId(it), client = this.client).let { sakJson ->
                    UUID.fromString(hentSakId(sakJson)) to Saksnummer(hentSaksnummer(sakJson).toLong())
                }
            }

            val sakMedUtløpSenere = opprettInnvilgetSøknadsbehandling(
                fnr = Fnr.generer().toString(),
                fraOgMed = 1.juli(2022).toString(),
                tilOgMed = 30.juni(2023).toString(),
                client = this.client,
                appComponents = appComponents,
            ).let {
                hentSak(BehandlingJson.hentSakId(it), client = this.client).let { sakJson ->
                    UUID.fromString(hentSakId(sakJson)) to Saksnummer(hentSaksnummer(sakJson).toLong())
                }
            }

            appComponents.services.sendPåminnelserOmNyStønadsperiodeService.sendPåminnelser()
            val jobContextPostgresRepo = JobContextPostgresRepo(
                sessionFactory = appComponents.databaseRepos.sessionFactory as PostgresSessionFactory,
            )
            val jobRepo = SendPåminnelseNyStønadsperiodeJobPostgresRepo(
                repo = jobContextPostgresRepo,
            )
            jobRepo.hent(
                SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(clock),
            ) shouldBe SendPåminnelseNyStønadsperiodeContext(
                clock = clock,
                id = NameAndYearMonthId(
                    name = "SendPåminnelseNyStønadsperiode",
                    yearMonth = jobbmåned,
                ),
                opprettet = Tidspunkt.now(clock),
                endret = Tidspunkt.now(clock),
                prosessert = setOf(
                    sakMedUtløpFørJobbmåneden.second,
                    sakMedUtløpMånedenEtterJobbmåneden.second,
                    sakMedUtløpSenere.second,
                ),
                sendt = setOf(
                    sakMedUtløpMånedenEtterJobbmåneden.second,
                ),
            )

            appComponents.databaseRepos.dokumentRepo.hentForSak(sakMedUtløpFørJobbmåneden.first).none {
                it.tittel.contains(PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode.tittel())
            }
            appComponents.databaseRepos.dokumentRepo.hentForSak(sakMedUtløpMånedenEtterJobbmåneden.first).single {
                it.tittel.contains(PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode.tittel())
            }
            appComponents.databaseRepos.dokumentRepo.hentForSak(sakMedUtløpSenere.first).none {
                it.tittel.contains(PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode.tittel())
            }

            val nySakMedUtløpMånedenEtterJobbmåneden = opprettInnvilgetSøknadsbehandling(
                fnr = Fnr.generer().toString(),
                fraOgMed = 1.januar(2022).toString(),
                tilOgMed = 31.august(2022).toString(),
                client = this.client,
                appComponents = appComponents,
            ).let {
                hentSak(BehandlingJson.hentSakId(it), client = this.client).let { sakJson ->
                    UUID.fromString(hentSakId(sakJson)) to Saksnummer(hentSaksnummer(sakJson).toLong())
                }
            }

            appComponents.services.sendPåminnelserOmNyStønadsperiodeService.sendPåminnelser()

            jobRepo.hent(
                SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(clock),
            ) shouldBe SendPåminnelseNyStønadsperiodeContext(
                clock = clock,
                id = NameAndYearMonthId(
                    name = "SendPåminnelseNyStønadsperiode",
                    yearMonth = jobbmåned,
                ),
                opprettet = Tidspunkt.now(clock),
                endret = Tidspunkt.now(clock),
                prosessert = setOf(
                    sakMedUtløpFørJobbmåneden.second,
                    sakMedUtløpMånedenEtterJobbmåneden.second,
                    sakMedUtløpSenere.second,
                    nySakMedUtløpMånedenEtterJobbmåneden.second,
                ),
                sendt = setOf(
                    sakMedUtløpMånedenEtterJobbmåneden.second,
                    nySakMedUtløpMånedenEtterJobbmåneden.second,
                ),
            )

            appComponents.databaseRepos.dokumentRepo.hentForSak(sakIdOgSaksnummer1.first).none {
                it.tittel.contains(PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode.tittel())
            }
            appComponents.databaseRepos.dokumentRepo.hentForSak(sakIdOgSaksnummer2.first).single {
                it.tittel.contains(PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode.tittel())
            }
            appComponents.databaseRepos.dokumentRepo.hentForSak(sakIdOgSaksnummer3.first).none {
                it.tittel.contains(PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode.tittel())
            }
            appComponents.databaseRepos.dokumentRepo.hentForSak(sakIdOgSaksnummer4.first).single {
                it.tittel.contains(PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode.tittel())
            }
        }
    }
}
