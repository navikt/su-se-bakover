package no.nav.su.se.bakover.database.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Year
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class ReguleringErSendtTilOppdragPostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `iverksatt regulering er sendt til oppdrag som default`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.reguleringRepo

        val (_, regulering) = testDataHelper.persisterReguleringIverksatt()

        repo.hent(regulering.id)!!.let { it as IverksattRegulering }.erSendtTilOppdrag shouldBe true
    }

    @Test
    fun `markerSomIkkeSendtTilOppdrag setter erSendtTilOppdrag til false`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.reguleringRepo

        val (_, regulering) = testDataHelper.persisterReguleringIverksatt()
        repo.markerSomIkkeSendtTilOppdrag(regulering.id)

        repo.hent(regulering.id)!!.let { it as IverksattRegulering }.erSendtTilOppdrag shouldBe false
    }

    @Test
    fun `markerSomSendtTilOppdrag setter erSendtTilOppdrag tilbake til true`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.reguleringRepo

        val (_, regulering) = testDataHelper.persisterReguleringIverksatt()
        repo.markerSomIkkeSendtTilOppdrag(regulering.id)
        repo.markerSomSendtTilOppdrag(regulering.id)

        repo.hent(regulering.id)!!.let { it as IverksattRegulering }.erSendtTilOppdrag shouldBe true
    }

    @Test
    fun `hentIverksatteReguleringerSomIkkeErSendtTilOppdrag returnerer kun de som ikke er sendt`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.reguleringRepo

        val (_, sendtRegulering) = testDataHelper.persisterReguleringIverksatt()
        val (_, ikkeSendtRegulering) = testDataHelper.persisterReguleringIverksatt()

        repo.markerSomIkkeSendtTilOppdrag(ikkeSendtRegulering.id)

        val år = Year.now(testDataHelper.clock)
        val result = repo.hentIverksatteReguleringerSomIkkeErSendtTilOppdrag(år)

        result.size shouldBe 1
        result.single().id shouldBe ikkeSendtRegulering.id
        result.single().erSendtTilOppdrag shouldBe false
        result.none { it.id == sendtRegulering.id } shouldBe true
    }

    @Test
    fun `hentIverksatteReguleringerSomIkkeErSendtTilOppdrag filtrerer på år`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.reguleringRepo

        val (_, regulering) = testDataHelper.persisterReguleringIverksatt()
        repo.markerSomIkkeSendtTilOppdrag(regulering.id)

        val riktigÅr = Year.now(testDataHelper.clock)
        val feilÅr = riktigÅr.minusYears(1)

        repo.hentIverksatteReguleringerSomIkkeErSendtTilOppdrag(riktigÅr).size shouldBe 1
        repo.hentIverksatteReguleringerSomIkkeErSendtTilOppdrag(feilÅr).size shouldBe 0
    }

    @Test
    fun `lagre overskriver ikke erSendtTilOppdrag-flagget`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.reguleringRepo

        val (_, regulering) = testDataHelper.persisterReguleringIverksatt()
        repo.markerSomIkkeSendtTilOppdrag(regulering.id)

        // Lagre reguleringen på nytt (som om retry-jobben lagrer noe unødvendig)
        repo.lagre(regulering)

        repo.hent(regulering.id)!!.let { it as IverksattRegulering }.erSendtTilOppdrag shouldBe false
    }
}
