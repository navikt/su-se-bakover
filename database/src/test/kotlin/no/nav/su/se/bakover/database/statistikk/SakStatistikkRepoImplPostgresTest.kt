package no.nav.su.se.bakover.database.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import statistikk.domain.SakStatistikk
import java.time.LocalDate
import java.util.UUID

internal class SakStatistikkRepoImplPostgresTest {
    @Test
    fun `Klarer å lagre behandlingshendelse som sakstatistikk`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakStatistikkRepo

            val sakstatistikk = lageSakStatistikkAlleVerdier()
            repo.lagreSakStatistikk(sakstatistikk)
            val lagret = repo.hentSakStatistikk(sakstatistikk.sakId)
            lagret.size shouldBe 1
            lagret.first() shouldBe sakstatistikk

            val sakstatistikkMedNull = lageSakStatistikkNullVerdier()
            repo.lagreSakStatistikk(sakstatistikkMedNull)
            val lagretMedNull = repo.hentSakStatistikk(sakstatistikkMedNull.sakId)
            lagretMedNull.first() shouldBe sakstatistikkMedNull
        }
    }

    private val tikkendeKlokke = TikkendeKlokke(fixedClock)

    private fun lageSakStatistikkAlleVerdier(): SakStatistikk {
        return SakStatistikk(
            hendelseTid = Tidspunkt.now(tikkendeKlokke),
            tekniskTid = Tidspunkt.now(tikkendeKlokke),
            sakId = UUID.randomUUID(),
            saksnummer = 123L,
            behandlingId = UUID.randomUUID(),
            relatertBehandlingId = UUID.randomUUID(),
            aktorId = Fnr.generer(),
            sakYtelse = "SU_ALDER",
            sakUtland = "NASJONAL",
            behandlingType = "SØKNAD",
            behandlingMetode = "MANUELL",
            mottattTid = Tidspunkt.now(tikkendeKlokke),
            registrertTid = Tidspunkt.now(tikkendeKlokke),
            ferdigbehandletTid = Tidspunkt.now(tikkendeKlokke),
            utbetaltTid = LocalDate.now(),
            behandlingStatus = "status",
            behandlingResultat = "resultat",
            resultatBegrunnelse = "begrunnelse",
            behandlingAarsak = "aarsak",
            opprettetAv = "opprettet_av",
            saksbehandler = "saksbehandler",
            ansvarligBeslutter = "ansvarlig_beslutter",
            ansvarligEnhet = "ansvarlig_enhet",
            vedtaksløsningNavn = "vedtaksløsningNavn",
            funksjonellPeriodeFom = LocalDate.now(),
            funksjonellPeriodeTom = LocalDate.now(),
            tilbakekrevBeløp = 12L,
        )
    }
    private fun lageSakStatistikkNullVerdier(): SakStatistikk {
        return SakStatistikk(
            hendelseTid = Tidspunkt.now(tikkendeKlokke),
            tekniskTid = Tidspunkt.now(tikkendeKlokke),
            sakId = UUID.randomUUID(),
            saksnummer = 123L,
            behandlingId = UUID.randomUUID(),
            aktorId = Fnr.generer(),
            sakYtelse = "SU_ALDER",
            sakUtland = "NASJONAL",
            behandlingType = "SØKNAD",
            behandlingMetode = "MANUELL",
            mottattTid = Tidspunkt.now(tikkendeKlokke),
            registrertTid = Tidspunkt.now(tikkendeKlokke),
            behandlingStatus = "status",
            opprettetAv = "opprettet_av",
            ansvarligEnhet = "ansvarlig_enhet",
            vedtaksløsningNavn = "vedtaksløsningNavn",
        )
    }
}
