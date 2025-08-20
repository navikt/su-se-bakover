package no.nav.su.se.bakover.database.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import statistikk.domain.StønadsklassifiseringDto
import statistikk.domain.StønadstatistikkDto
import statistikk.domain.StønadstatistikkMåned
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class StønadMånedStatistikkRepoImplTest {

    @Test
    fun `kan lagre månedlig stønad statistikk`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.stønadMånedStatistikkRepo

            val månedstatistikk = lagStønadMånedStatistikk()

            repo.lagreMånedStatistikk(månedstatistikk)
            val result = repo.hentMånedStatistikk(YearMonth.of(2025, 6))

            result.single() shouldBe månedstatistikk
        }
    }

    companion object {
        fun lagStønadMånedStatistikk(): StønadstatistikkMåned {
            val måned = YearMonth.of(2025, 6)
            return StønadstatistikkMåned(
                id = UUID.randomUUID(),
                måned = måned,
                vedtaksdato = LocalDate.of(2025, 6, 6),
                personnummer = Fnr("01111111111"),
                månedsbeløp = StønadstatistikkDto.Månedsbeløp(
                    måned = måned.toString(),
                    stonadsklassifisering = StønadsklassifiseringDto.BOR_ALENE,
                    bruttosats = 0,
                    nettosats = 0,
                    inntekter = emptyList(),
                    fradragSum = 0,
                ),
            )
        }
    }
}
