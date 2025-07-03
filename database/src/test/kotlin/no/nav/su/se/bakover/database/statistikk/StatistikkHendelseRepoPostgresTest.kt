package no.nav.su.se.bakover.database.statistikk

import io.kotest.inspectors.forExactly
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.JaNei
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import statistikk.domain.StønadsklassifiseringDto
import statistikk.domain.StønadstatistikkDto
import statistikk.domain.StønadstatistikkDto.Månedsbeløp
import statistikk.domain.StønadstatistikkDto.Stønadstype
import statistikk.domain.StønadstatistikkDto.Vedtaksresultat
import statistikk.domain.StønadstatistikkDto.Vedtakstype
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

internal class StatistikkHendelseRepoPostgresTest {
    private val tikkendeKlokke = TikkendeKlokke(fixedClock)

    fun genererBasicStønadsstatistikk(list: List<Månedsbeløp>): StønadstatistikkDto {
        return StønadstatistikkDto(
            harUtenlandsOpphold = null,
            harFamiliegjenforening = null,
            statistikkAarMaaned = YearMonth.now(),
            personnummer = Fnr.generer(),
            personNummerEktefelle = Fnr.generer(),
            funksjonellTid = Tidspunkt.now(tikkendeKlokke),
            tekniskTid = Tidspunkt.now(tikkendeKlokke),
            stonadstype = Stønadstype.SU_ALDER,
            sakId = UUID.randomUUID(),
            vedtaksdato = LocalDate.now(),
            vedtakstype = Vedtakstype.REVURDERING,
            vedtaksresultat = Vedtaksresultat.INNVILGET,
            behandlendeEnhetKode = "4815",
            ytelseVirkningstidspunkt = LocalDate.now(),
            gjeldendeStonadVirkningstidspunkt = LocalDate.now(),
            gjeldendeStonadStopptidspunkt = LocalDate.now(),
            gjeldendeStonadUtbetalingsstart = LocalDate.now(),
            gjeldendeStonadUtbetalingsstopp = LocalDate.now(),
            månedsbeløp = list,
            opphorsgrunn = null,
            opphorsdato = null,
            flyktningsstatus = null,
            versjon = null,
        )
    }

    @Test
    fun `Klarer å lagre stønadshendelse uten månedsbeløp`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.statistikkHendelseRepo
            val stønadshendelse = genererBasicStønadsstatistikk(list = emptyList())
            repo.lagreHendelse(stønadshendelse)
            val hendelser = repo.hentHendelserForFnr(stønadshendelse.personnummer)
            hendelser.size shouldBe 1
            hendelser.first() shouldBe stønadshendelse
        }
    }

    @Test
    fun `Klarer å lagre stønadshendelse med månedsbeløp & inntekter`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.statistikkHendelseRepo
            val stønadshendelse = genererBasicStønadsstatistikk(
                list = listOf(
                    Månedsbeløp(
                        YearMonth.now().toString(),
                        StønadsklassifiseringDto.BOR_MED_EKTEFELLE_UNDER_67_UFØR_FLYKTNING,
                        123L,
                        123L,
                        listOf(
                            StønadstatistikkDto.Inntekt(Fradragstype.Arbeidsinntekt.kategori.name, 123L, FradragTilhører.BRUKER.toString(), false),
                            StønadstatistikkDto.Inntekt(Fradragstype.ForventetInntekt.kategori.name, 0L, FradragTilhører.BRUKER.toString(), false),
                        ),
                        123L,
                    ),
                ),
            )
            repo.lagreHendelse(stønadshendelse)
            val hendelser = repo.hentHendelserForFnr(stønadshendelse.personnummer)
            hendelser.size shouldBe 1
            val hendelse = hendelser.first()
            hendelse shouldBe stønadshendelse
            val førsteMånedsbeløp = hendelse.månedsbeløp.first()
            førsteMånedsbeløp.stonadsklassifisering shouldBe StønadsklassifiseringDto.BOR_MED_EKTEFELLE_UNDER_67_UFØR_FLYKTNING
            førsteMånedsbeløp.inntekter.size shouldBe 2
            førsteMånedsbeløp.inntekter.forExactly(1) { it.inntektstype shouldBe Fradragstype.ForventetInntekt.kategori.name }
            førsteMånedsbeløp.inntekter.forExactly(1) { it.inntektstype shouldBe Fradragstype.Arbeidsinntekt.kategori.name }
            førsteMånedsbeløp.inntekter.forExactly(1) { it.beløp shouldBe 0L }
        }
    }

    // TODO: lag egen domain elns for statstikk bygger i fellest test så vi kan bygge opp data her
    @Test
    fun `Klarer å lagre gjenopptak med månedsbeløp & inntekter`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.statistikkHendelseRepo
            // hentet fra [StønadsstatistikkTest.Gjenopptak sender med riktig månedsbeløp]
            val stønadshendelseGjenopptak = StønadstatistikkDto(
                harUtenlandsOpphold = JaNei.NEI,
                harFamiliegjenforening = null, // not in JSON, so null
                statistikkAarMaaned = YearMonth.parse("2025-07"),
                personnummer = Fnr("64825004709"),
                personNummerEktefelle = null,
                funksjonellTid = Tidspunkt.parse("2021-01-01T01:02:58.456789Z"),
                tekniskTid = Tidspunkt.parse("2021-01-01T01:02:03.456789Z"),
                stonadstype = Stønadstype.SU_UFØR,
                sakId = UUID.fromString("6d451208-d0f2-410e-993b-40a2dc56f4fb"),
                vedtaksdato = LocalDate.parse("2021-01-01"),
                vedtakstype = Vedtakstype.GJENOPPTAK,
                vedtaksresultat = Vedtaksresultat.GJENOPPTATT,
                behandlendeEnhetKode = "4815",
                ytelseVirkningstidspunkt = LocalDate.parse("2021-01-01"),
                gjeldendeStonadVirkningstidspunkt = LocalDate.parse("2021-01-01"),
                gjeldendeStonadStopptidspunkt = LocalDate.parse("2021-01-31"),
                gjeldendeStonadUtbetalingsstart = LocalDate.parse("2021-01-01"),
                gjeldendeStonadUtbetalingsstopp = LocalDate.parse("2021-01-31"),
                månedsbeløp = listOf(
                    Månedsbeløp(
                        måned = "2021-01-01",
                        stonadsklassifisering = StønadsklassifiseringDto.BOR_ALENE, // assuming enum or constant
                        bruttosats = 20946L,
                        nettosats = 17946L,
                        inntekter = listOf(
                            StønadstatistikkDto.Inntekt(
                                inntektstype = Fradragstype.ForventetInntekt.kategori.name,
                                beløp = 3000L,
                                tilhører = FradragTilhører.BRUKER.toString(),
                                erUtenlandsk = false,
                            ),
                        ),
                        fradragSum = 3000L,
                    ),
                ),
                opphorsgrunn = null,
                opphorsdato = null,
                flyktningsstatus = "FLYKTNING",
                versjon = UUID.randomUUID().toString(),
            )

            repo.lagreHendelse(stønadshendelseGjenopptak)
            val hendelser = repo.hentHendelserForFnr(stønadshendelseGjenopptak.personnummer)
            hendelser.size shouldBe 1
            val hendelse = hendelser.first()

            hendelse shouldBe stønadshendelseGjenopptak
            val førsteMånedsbeløp = hendelse.månedsbeløp.first()
            førsteMånedsbeløp.stonadsklassifisering shouldBe StønadsklassifiseringDto.BOR_ALENE
            førsteMånedsbeløp.inntekter.forExactly(1) { it.inntektstype shouldBe Fradragstype.ForventetInntekt.kategori.name }
        }
    }
}
