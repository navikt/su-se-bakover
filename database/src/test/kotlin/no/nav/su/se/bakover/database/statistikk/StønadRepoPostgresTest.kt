package no.nav.su.se.bakover.database.statistikk

import io.kotest.inspectors.forExactly
import io.kotest.matchers.shouldBe
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

internal class StønadRepoPostgresTest {
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
            vedtakstype = Vedtakstype.REVURDERING, // todo sjekk om disse stemmer med de vi kan sender ovder
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
}
