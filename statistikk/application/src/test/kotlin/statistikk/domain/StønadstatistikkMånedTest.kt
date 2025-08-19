package statistikk.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import statistikk.domain.StønadstatistikkDto.Månedsbeløp
import statistikk.domain.StønadstatistikkDto.Stønadstype
import statistikk.domain.StønadstatistikkDto.Vedtaksresultat
import statistikk.domain.StønadstatistikkDto.Vedtakstype
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class StønadstatistikkMånedTest {

    @Test
    fun `summerMånedStatistikk skal kun bruke etterspurt måned`() {
        val juni = YearMonth.of(2025, 6)
        val stønadstatistikk = listOf(
            lagStønadstatistikk(YearMonth.of(2025, 5), LocalDate.of(2025, 5, 10), sakEn.first, sakEn.second, 100L),
            lagStønadstatistikk(YearMonth.of(2025, 6), LocalDate.of(2025, 5, 11), sakEn.first, sakEn.second, 200L),

            lagStønadstatistikk(YearMonth.of(2025, 4), LocalDate.of(2025, 5, 10), sakTo.first, sakTo.second, 300L),
            lagStønadstatistikk(YearMonth.of(2025, 6), LocalDate.of(2025, 5, 11), sakTo.first, sakTo.second, 400L),
            lagStønadstatistikk(YearMonth.of(2025, 8), LocalDate.of(2025, 5, 12), sakTo.first, sakTo.second, 500L),

            lagStønadstatistikk(YearMonth.of(2025, 5), LocalDate.of(2025, 5, 10), sakTre.first, sakTre.second, 600L),
            lagStønadstatistikk(YearMonth.of(2025, 6), LocalDate.of(2025, 5, 11), sakTre.first, sakTre.second, 700L),
        )

        val result = stønadstatistikk.sisteStatistikkPerMåned(juni)

        result.size shouldBe 3
        result.forEach {
            it.måned shouldBe juni
        }
    }

    @Test
    fun `summerMånedStatistikk skal kun bruke siste statistikk for hver måned`() {
        val juni = YearMonth.of(2025, 6)
        val stønadstatistikk = listOf(
            lagStønadstatistikk(juni, LocalDate.of(2025, 5, 10), sakEn.first, sakEn.second, 100L),
            lagStønadstatistikk(juni, LocalDate.of(2025, 5, 11), sakEn.first, sakEn.second, 200L),

            lagStønadstatistikk(juni, LocalDate.of(2025, 5, 10), sakTo.first, sakTo.second, 300L),
            lagStønadstatistikk(juni, LocalDate.of(2025, 5, 11), sakTo.first, sakTo.second, 400L),
            lagStønadstatistikk(juni, LocalDate.of(2025, 5, 12), sakTo.first, sakTo.second, 500L),

            lagStønadstatistikk(juni, LocalDate.of(2025, 5, 10), sakTre.first, sakTre.second, 600L),
            lagStønadstatistikk(juni, LocalDate.of(2025, 5, 11), sakTre.first, sakTre.second, 700L),
        )

        val result = stønadstatistikk.sisteStatistikkPerMåned(juni)

        result.size shouldBe 3
        with(result[0]) {
            måned shouldBe juni
            vedtaksdato shouldBe LocalDate.of(2025, 5, 11)
        }
        with(result[1]) {
            måned shouldBe juni
            vedtaksdato shouldBe LocalDate.of(2025, 5, 12)
        }
        with(result[2]) {
            måned shouldBe juni
            vedtaksdato shouldBe LocalDate.of(2025, 5, 11)
        }
    }

    companion object {

        private val tikkendeKlokke = TikkendeKlokke(fixedClock)
        val sakEn = Fnr.generer() to UUID.randomUUID()
        val sakTo = Fnr.generer() to UUID.randomUUID()
        val sakTre = Fnr.generer() to UUID.randomUUID()

        fun lagStønadstatistikk(
            måned: YearMonth,
            vedtaksdato: LocalDate = LocalDate.now(),
            personid: Fnr,
            sakid: UUID,
            inntekt: Long = 0L,
        ): StønadstatistikkDto {
            val start = måned.minusMonths(2).atDay(1)
            val slutt = måned.plusMonths(2).atEndOfMonth()

            val månedsbeløp = Månedsbeløp(
                måned = måned.toString(),
                stonadsklassifisering = StønadsklassifiseringDto.BOR_ALENE,
                bruttosats = inntekt,
                nettosats = inntekt,
                fradragSum = inntekt,
                inntekter = listOf(),

            )

            return StønadstatistikkDto(
                harUtenlandsOpphold = null,
                harFamiliegjenforening = null,
                statistikkAarMaaned = måned,
                personnummer = personid,
                personNummerEktefelle = Fnr.generer(),
                funksjonellTid = Tidspunkt.now(tikkendeKlokke),
                tekniskTid = Tidspunkt.now(tikkendeKlokke),
                stonadstype = Stønadstype.SU_ALDER,
                sakId = sakid,
                vedtaksdato = vedtaksdato,
                vedtakstype = Vedtakstype.REVURDERING,
                vedtaksresultat = Vedtaksresultat.INNVILGET,
                behandlendeEnhetKode = "4815",
                ytelseVirkningstidspunkt = start,
                gjeldendeStonadVirkningstidspunkt = start,
                gjeldendeStonadStopptidspunkt = slutt,
                gjeldendeStonadUtbetalingsstart = start,
                gjeldendeStonadUtbetalingsstopp = slutt,
                månedsbeløp = listOf(månedsbeløp),
                opphorsgrunn = null,
                opphorsdato = slutt,
                flyktningsstatus = null,
                versjon = null,
            )
        }
    }
}
