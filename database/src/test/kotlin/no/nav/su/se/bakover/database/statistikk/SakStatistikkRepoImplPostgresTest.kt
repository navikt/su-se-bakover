package no.nav.su.se.bakover.database.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import statistikk.domain.SakStatistikk

internal class SakStatistikkRepoImplPostgresTest {
    private val tikkendeKlokke = TikkendeKlokke(fixedClock)

    @Test
    fun `Klarer å lagre behandlingshendelse som sakstatistikk`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakStatistikkRepo
            val sakstatistikk = lageSakStatistikk()
            repo.lagreSakStatistikk(sakstatistikk)
            val lagret = repo.hentSakStatistikk(sakstatistikk.sakId)
            lagret.size shouldBe 1
            lagret.first() shouldBe sakstatistikk
        }
    }

    fun lageSakStatistikk(): SakStatistikk {
        return SakStatistikk(
            saksnummer = TODO(),
            behandlingId = TODO(),
            relatertBehandlingId = TODO(),
            aktorId = TODO(),
            sakYtelse = TODO(),
            sakUtland = TODO(),
            behandlingType = TODO(),
            behandlingMetode = TODO(),
            mottattTid = TODO(),
            registrertTid = TODO(),
            ferdigbehandletTid = TODO(),
            utbetaltTid = TODO(),
            behandlingStatus = TODO(),
            behandlingResultat = TODO(),
            resultatBegrunnelse = TODO(),
            behandlingAarsak = TODO(),
            opprettetAv = TODO(),
            saksbehandler = TODO(),
            ansvarligBeslutter = TODO(),
            ansvarligEnhet = TODO(),
            vedtakslsløsningNavn = TODO(),
            funksjonellPeriodeFom = TODO(),
            funksjonellPeriodeTom = TODO(),
            tilbakekrevingBeløp = TODO(),
            funksjonellTid = TODO(),
            tekniskTid = TODO(),
            sakId = TODO(),
        )
    }
}
