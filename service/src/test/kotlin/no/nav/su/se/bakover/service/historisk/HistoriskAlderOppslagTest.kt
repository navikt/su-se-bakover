package no.nav.su.se.bakover.service.historisk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAlderProjeksjonRepo
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAldersstønad
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskKode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtaksperiode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskYtelseForMåned
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskYtelsesperiode
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

internal class HistoriskAlderOppslagTest {
    @Test
    fun `lager komplett tidslinje med eksplisitte hull og støtter periodeoppslag`() {
        val repo =
            RepoFake(
                perioder =
                listOf(
                    ytelsesperiode("2020-01-01", "2020-02-29", "40"),
                    ytelsesperiode("2020-04-01", "2020-04-30", "41"),
                ),
            )
        val oppslag = HistoriskAlderOppslag(repo)

        val tidslinje =
            oppslag.hentTidslinje(
                personident = "12345678910",
                fraOgMed = YearMonth.of(2020, 1),
                tilOgMed = YearMonth.of(2020, 4),
            )

        tidslinje.måneder.map { it::class } shouldBe
            listOf(
                HistoriskYtelseForMåned.Ytelse::class,
                HistoriskYtelseForMåned.Ytelse::class,
                HistoriskYtelseForMåned.IngenYtelse::class,
                HistoriskYtelseForMåned.Ytelse::class,
            )
        (tidslinje.måneder.first() as HistoriskYtelseForMåned.Ytelse).also {
            it.bosituasjon shouldBe HistoriskKode("EO", HistoriskBosituasjon.EPS_OVER_67)
            it.årligYtelsesbeløp shouldBe BigDecimal("202428")
        }
        oppslag.harYtelsePåDato("12345678910", LocalDate.of(2020, 1, 15)) shouldBe true
        oppslag.harYtelsePåDato("12345678910", LocalDate.of(2020, 3, 15)) shouldBe false
        oppslag.harYtelseIMinstÉnMåned(
            "12345678910",
            YearMonth.of(2020, 2),
            YearMonth.of(2020, 3),
        ) shouldBe true
        oppslag.harYtelseIHelePerioden(
            "12345678910",
            YearMonth.of(2020, 1),
            YearMonth.of(2020, 4),
        ) shouldBe false
    }

    private fun ytelsesperiode(fraOgMed: String, tilOgMed: String, vedtakId: String) =
        HistoriskYtelsesperiode(
            stønadId = HistoriskStønadId("20"),
            vedtakId = HistoriskVedtakId(vedtakId),
            fraOgMed = LocalDate.parse(fraOgMed),
            tilOgMed = LocalDate.parse(tilOgMed),
            sats = BigDecimal("16869"),
            fradrag = BigDecimal("5622"),
            bosituasjon = HistoriskKode("EO", HistoriskBosituasjon.EPS_OVER_67),
            årligYtelsesbeløp = BigDecimal("202428"),
        )

    private class RepoFake(
        private val perioder: List<HistoriskYtelsesperiode>,
    ) : HistoriskAlderProjeksjonRepo {
        override fun startProjeksjon(importId: UUID) = Unit

        override fun lagreBatch(importId: UUID, stønader: List<HistoriskAldersstønad>) = Unit

        override fun fullførProjeksjon(importId: UUID) = Unit

        override fun markerFeilet(importId: UUID, beskrivelse: String) = Unit

        override fun harSak(personident: String) = true

        override fun hentVedtaksperioder(personident: String): List<HistoriskVedtaksperiode> = emptyList()

        override fun hentYtelsesperioder(
            personident: String,
            fraOgMed: LocalDate,
            tilOgMed: LocalDate,
        ): List<HistoriskYtelsesperiode> =
            perioder.filter { it.fraOgMed <= tilOgMed && it.tilOgMed >= fraOgMed }
    }
}
