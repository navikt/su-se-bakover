package no.nav.su.se.bakover.database.statistikk

import BehandlingStatus
import arrow.core.right
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.domain.statistikk.BehandlingMetode
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.personMedAdresse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import person.domain.Adressebeskyttelse
import person.domain.PersonOppslag
import java.math.BigInteger
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class SakStatistikkRepoImplPostgresTest(private val dataSource: DataSource) {
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `kode 6 gir enhet 2103 på riktig saks statistikk uten å endre status eller andre saker`(
        kode6HosSøker: Boolean,
    ) = runBlocking<Unit> {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.sakStatistikkRepo
        val personOppslag = mock<PersonOppslag>()
        val service = SakStatistikkService(repo, fixedClock, personOppslag, testDataHelper.sakRepo)
        whenever(personOppslag.person(any(), any()))
            .thenReturn(personMedAdresse().copy(adressebeskyttelse = Adressebeskyttelse.UGRADERT.name).right())
        val fiktivSaksbehandler = NavIdentBruker.Saksbehandler("V123456")
        val registrert = BehandlingStatus.Registrert.toString()
        val (sak, behandling) = testDataHelper.persisternySøknadsbehandlingMedStønadsperiode(
            saksbehandler = fiktivSaksbehandler,
        )
        val epsFnr = requireNotNull(behandling.søknad.søknadInnhold.boforhold.ektefellePartnerSamboer).fnr
        val kode6Person = if (kode6HosSøker) sak.fnr else epsFnr
        whenever(personOppslag.person(kode6Person, sak.type))
            .thenReturn(personMedAdresse().copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG.name).right())
        val vurdering = service.vurderForBruker(behandling)

        testDataHelper.sessionFactory.withTransactionContext { tx ->
            service.lagreForBrukerflyt(StatistikkEvent.Behandling.Søknad.Opprettet(behandling, fiktivSaksbehandler), vurdering, tx)
        }

        val rad = repo.hentSakStatistikk(sak.id).single()
        rad.sakId shouldBe sak.id
        rad.saksnummer shouldBe sak.saksnummer.nummer
        rad.behandlingId shouldBe behandling.id.value
        rad.ansvarligEnhet shouldBe "2103"
        rad.behandlingStatus shouldBe registrert
        testDataHelper.sakRepo.hentSak(sak.id) shouldBe sak

        val ordinærSaksbehandler = no.nav.su.se.bakover.test.saksbehandler
        val (ordinærSak, ordinærBehandling) = testDataHelper.persisternySøknadsbehandlingMedStønadsperiode(
            saksbehandler = ordinærSaksbehandler,
        )
        whenever(personOppslag.person(any(), any()))
            .thenReturn(personMedAdresse().copy(adressebeskyttelse = Adressebeskyttelse.UGRADERT.name).right())
        val ordinærVurdering = service.vurderForBruker(ordinærBehandling)
        testDataHelper.sessionFactory.withTransactionContext { tx ->
            service.lagreForBrukerflyt(
                StatistikkEvent.Behandling.Søknad.Opprettet(ordinærBehandling, ordinærSaksbehandler),
                ordinærVurdering,
                tx,
            )
        }

        val ordinærRad = repo.hentSakStatistikk(ordinærSak.id).single()
        ordinærRad.ansvarligEnhet shouldBe "4815"
        ordinærRad.behandlingStatus shouldBe registrert
        repo.hentSakStatistikk(sak.id).single() shouldBe rad
    }

    @Test
    fun `Klarer å lagre behandlingshendelse som sakstatistikk`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.sakStatistikkRepo

        val sakstatistikk = lageSakStatistikkAlleVerdier()
        repo.lagreSakStatistikk(sakstatistikk)
        val lagret = repo.hentSakStatistikk(sakstatistikk.sakId)
        lagret.size shouldBe 1
        lagret.first() shouldBe sakstatistikk.copy(
            sekvensId = BigInteger.valueOf(1),
        )

        val sakstatistikkMedNull = lageSakStatistikkNullVerdier()
        repo.lagreSakStatistikk(sakstatistikkMedNull)
        val lagretMedNull = repo.hentSakStatistikk(sakstatistikkMedNull.sakId)
        lagretMedNull.first() shouldBe sakstatistikkMedNull.copy(
            sekvensId = BigInteger.valueOf(2),
        )

        val sekvensIder = setOf(
            lagretMedNull.first().getSekvensId().longValueExact(),
            lagret.first().getSekvensId().longValueExact(),
        )
        val forventedeRader = listOf(
            lagret.first(),
            lagretMedNull.first(),
        )
        repo.hentSakStatistikk(sekvensIder) shouldBe forventedeRader
    }

    private val tikkendeKlokke = TikkendeKlokke(fixedClock)

    private fun lageSakStatistikkAlleVerdier(): SakStatistikk {
        return SakStatistikk(
            funksjonellTid = Tidspunkt.now(tikkendeKlokke),
            tekniskTid = Tidspunkt.now(tikkendeKlokke),
            sakId = UUID.randomUUID(),
            saksnummer = 123L,
            behandlingId = UUID.randomUUID(),
            relatertBehandlingId = UUID.randomUUID(),
            aktorId = Fnr.generer(),
            sakYtelse = "SU_ALDER",
            sakUtland = "NASJONAL",
            behandlingType = "SØKNAD",
            behandlingMetode = BehandlingMetode.MANUELL,
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
            funksjonellPeriodeFom = LocalDate.now(),
            funksjonellPeriodeTom = LocalDate.now(),
            tilbakekrevBeløp = 12L,
        )
    }

    private fun lageSakStatistikkNullVerdier(): SakStatistikk {
        return SakStatistikk(
            funksjonellTid = Tidspunkt.now(tikkendeKlokke),
            tekniskTid = Tidspunkt.now(tikkendeKlokke),
            sakId = UUID.randomUUID(),
            saksnummer = 123L,
            behandlingId = UUID.randomUUID(),
            aktorId = Fnr.generer(),
            sakYtelse = "SU_ALDER",
            sakUtland = "NASJONAL",
            behandlingType = "SØKNAD",
            behandlingMetode = BehandlingMetode.MANUELL,
            mottattTid = Tidspunkt.now(tikkendeKlokke),
            registrertTid = Tidspunkt.now(tikkendeKlokke),
            behandlingStatus = "status",
            opprettetAv = "opprettet_av",
            ansvarligEnhet = "ansvarlig_enhet",
        )
    }
}
