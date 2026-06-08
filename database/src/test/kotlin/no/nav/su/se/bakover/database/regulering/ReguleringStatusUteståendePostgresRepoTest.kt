package no.nav.su.se.bakover.database.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.regulering.ProdusertReguleringStatus
import no.nav.su.se.bakover.domain.regulering.ReguleringStatus
import no.nav.su.se.bakover.domain.regulering.SakMedGammeltGrunnbeløp
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import satser.domain.SatsFactory
import satser.domain.Satskategori

internal class ReguleringStatusUteståendePostgresRepoTest {

    @Test
    fun `lagreOppstartet lagrer med status Pågående og null reguleringStatus`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.databaseRepos.reguleringStatusRepo

            val id = repo.lagreOppstartet()

            val result = repo.hent()
            result.size shouldBe 1
            result.single().let {
                it.id shouldBe id
                it.produserStatus shouldBe ProdusertReguleringStatus.ProduserStatus.Pågående
                it.reguleringStatus shouldBe null
            }
        }
    }

    @Test
    fun `lagreProdusert oppdaterer status til Fullført med reguleringStatus`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.databaseRepos.reguleringStatusRepo

            val id = repo.lagreOppstartet()
            val reguleringStatus = lagTestReguleringStatus()

            repo.lagreProdusert(id, reguleringStatus)

            val result = repo.hent()
            result.size shouldBe 1
            result.single().let {
                it.id shouldBe id
                it.produserStatus shouldBe ProdusertReguleringStatus.ProduserStatus.Fullført
                it.reguleringStatus shouldBe reguleringStatus
            }
        }
    }

    @Test
    fun `lagreFeilet oppdaterer status til Feilet`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.databaseRepos.reguleringStatusRepo

            val id = repo.lagreOppstartet()

            repo.lagreFeilet(id)

            val result = repo.hent()
            result.size shouldBe 1
            result.single().let {
                it.id shouldBe id
                it.produserStatus shouldBe ProdusertReguleringStatus.ProduserStatus.Feilet
                it.reguleringStatus shouldBe null
            }
        }
    }

    @Test
    fun `hentPågående returnerer kun pågående`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.databaseRepos.reguleringStatusRepo

            val id1 = repo.lagreOppstartet()
            val id2 = repo.lagreOppstartet()
            repo.lagreProdusert(id1, lagTestReguleringStatus())

            val pågående = repo.hentPågående()
            pågående.size shouldBe 1
            pågående.single().id shouldBe id2
        }
    }

    private fun lagTestReguleringStatus() = ReguleringStatus(
        aar = 2026,
        sisteGrunnbeløpOgSatser = SatsFactory.SisteGrunnbeløpOgSatser(
            grunnbeløp = 124028,
            garantipensjonOrdinærMåned = 15000.0,
            garantipensjonHøyMåned = 16000.0,
        ),
        sakerMedUtebetalingIMai = 5,
        sakerMedGammelG = 1,
        utenÅpenRegulering = listOf(
            SakMedGammeltGrunnbeløp(
                saksnummer = Saksnummer(2021),
                type = Sakstype.UFØRE,
                benyttetGrunnbeløp = 118620,
                benyttetSatskategori = Satskategori.ORDINÆR,
                benyttetSats = 2.28,
            ),
        ),
    )
}
