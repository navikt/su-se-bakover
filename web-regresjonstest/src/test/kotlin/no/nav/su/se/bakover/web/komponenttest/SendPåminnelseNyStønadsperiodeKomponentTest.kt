package no.nav.su.se.bakover.web.komponenttest

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.JobContext
import no.nav.su.se.bakover.domain.NameAndYearMonthId
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.SakJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Month
import java.time.YearMonth
import java.time.ZoneOffset

class SendPåminnelseNyStønadsperiodeKomponentTest {
    @Test
    fun `sender påminnelser for saker med utløp i inneværende måned`() {
        val clock = Clock.fixed(2.juli(2022).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        withKomptestApplication(
            clock = clock,
        ) { appComponents ->
            val saksnummer1 = opprettInnvilgetSøknadsbehandling(
                fnr = Fnr.generer().toString(),
                fraOgMed = 1.januar(2021).toString(),
                tilOgMed = 31.desember(2021).toString(),
            ).let {
                hentSak(BehandlingJson.hentSakId(it))
            }

            val saksnummer2 = opprettInnvilgetSøknadsbehandling(
                fnr = Fnr.generer().toString(),
                fraOgMed = 1.august(2021).toString(),
                tilOgMed = 31.juli(2022).toString(),
            ).let {
                hentSak(BehandlingJson.hentSakId(it))
            }

            val saksnummer3 = opprettInnvilgetSøknadsbehandling(
                fnr = Fnr.generer().toString(),
                fraOgMed = 1.juli(2022).toString(),
                tilOgMed = 30.juni(2023).toString(),
            ).let {
                hentSak(BehandlingJson.hentSakId(it))
            }

            appComponents.services.sendPåminnelseNyStønadsperiodeService.sendPåminnelser()

            appComponents.databaseRepos.jobContextRepo.hent<JobContext.SendPåminnelseNyStønadsperiodeContext>(
                JobContext.SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(clock),
            ) shouldBe JobContext.SendPåminnelseNyStønadsperiodeContext(
                clock = clock,
                id = NameAndYearMonthId(
                    jobName = "SendPåminnelseNyStønadsperiode",
                    yearMonth = YearMonth.of(2022, Month.JULY),
                ),
                opprettet = Tidspunkt.now(clock),
                endret = Tidspunkt.now(clock),
                prosessert = setOf(
                    Saksnummer(SakJson.hentSaksnummer(saksnummer1).toLong()),
                    Saksnummer(SakJson.hentSaksnummer(saksnummer2).toLong()),
                    Saksnummer(SakJson.hentSaksnummer(saksnummer3).toLong()),
                ),
                sendt = setOf(
                    Saksnummer(SakJson.hentSaksnummer(saksnummer2).toLong()),
                ),
            )
        }
    }
}
