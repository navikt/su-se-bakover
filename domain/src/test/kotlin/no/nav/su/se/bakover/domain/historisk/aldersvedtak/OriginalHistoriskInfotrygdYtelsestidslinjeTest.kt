package no.nav.su.se.bakover.domain.historisk.aldersvedtak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class OriginalHistoriskInfotrygdYtelsestidslinjeTest {
    @Test
    fun `velger senest registrerte vedtak og bruker vedtakId som tie-breaker`() {
        val periode = Periode.create(januar.fraOgMed, mars.tilOgMed)
        val nyesteRegistrering = "2020-02-10T10:00:00"
        val høyesteVedtakId = 12L
        val forventetVedtakId = HistoriskVedtakId(høyesteVedtakId)
        val eldreVedtak = grunnlag(
            vedtakId = 10,
            registrertTidspunkt = "2020-01-10T10:00:00",
            sats = sats,
        )
        val nyereVedtak = grunnlag(
            vedtakId = 11,
            registrertTidspunkt = nyesteRegistrering,
            sats = 11_000,
        )
        val høyereVedtakId = grunnlag(
            vedtakId = høyesteVedtakId,
            registrertTidspunkt = nyesteRegistrering,
            sats = 12_000,
        )

        val tidslinje = OriginalHistoriskInfotrygdYtelsestidslinje.bygg(
            projeksjonId = projeksjonId,
            periode = periode,
            grunnlag = listOf(eldreVedtak, nyereVedtak, høyereVedtakId),
        )

        tidslinje.måneder.values.map {
            (it as HistoriskInfotrygdYtelseForMåned.Ytelse).vedtakId
        } shouldBe listOf(forventetVedtakId, forventetVedtakId, forventetVedtakId)
    }

    @Test
    fun `ignorerer annullerte og uendrede vedtak`() {
        val periode = januar
        val ytelsesvedtakId = 10L
        val ytelsesvedtak = grunnlag(vedtakId = ytelsesvedtakId, registrertTidspunkt = "2020-01-10T10:00:00")
        val annullertVedtak = grunnlag(
            vedtakId = 11,
            registrertTidspunkt = "2020-02-10T10:00:00",
            endringskoder = listOf("AN"),
        )
        val uendretVedtak = grunnlag(
            vedtakId = 12,
            registrertTidspunkt = "2020-03-10T10:00:00",
            resultat = HistoriskResultat.UENDRET,
        )

        val tidslinje = OriginalHistoriskInfotrygdYtelsestidslinje.bygg(
            projeksjonId = projeksjonId,
            periode = periode,
            grunnlag = listOf(ytelsesvedtak, annullertVedtak, uendretVedtak),
        )

        (tidslinje.måneder.getValue(periode) as HistoriskInfotrygdYtelseForMåned.Ytelse)
            .vedtakId shouldBe HistoriskVedtakId(ytelsesvedtakId)
    }

    @Test
    fun `gir eksplisitt ingen ytelse for hull og manglende månedsbeløp`() {
        val periode = Periode.create(januar.fraOgMed, mars.tilOgMed)
        val vedtakId = 10L
        val forventetVedtakId = HistoriskVedtakId(vedtakId)
        val grunnlag = grunnlag(
            vedtakId = vedtakId,
            registrertTidspunkt = "2020-01-10T10:00:00",
            fraOgMed = februar.fraOgMed,
            tilOgMed = mars.tilOgMed,
            beløpFraOgMed = februar.fraOgMed,
            beløpTilOgMed = februar.tilOgMed,
        )

        val tidslinje = OriginalHistoriskInfotrygdYtelsestidslinje.bygg(
            projeksjonId = projeksjonId,
            periode = periode,
            grunnlag = listOf(grunnlag),
        )

        tidslinje.måneder.values shouldBe listOf(
            HistoriskInfotrygdYtelseForMåned.IngenYtelse(
                måned = januar,
                årsak = HistoriskInfotrygdIngenYtelseÅrsak.INGEN_GJELDENDE_VEDTAK,
            ),
            HistoriskInfotrygdYtelseForMåned.Ytelse(
                måned = februar,
                stønadId = stønadId,
                vedtakId = forventetVedtakId,
                oppdragId = oppdragId,
                bosituasjon = HistoriskBosituasjon.ENSLIG,
                sats = BigDecimal(sats),
                fradrag = fradrag,
                fradragskoder = fradragskoder,
            ),
            HistoriskInfotrygdYtelseForMåned.IngenYtelse(
                måned = mars,
                årsak = HistoriskInfotrygdIngenYtelseÅrsak.MANGLER_MÅNEDSBELØP,
                vedtakId = forventetVedtakId,
            ),
        )
    }

    @Test
    fun `avgrenser med stønadens periode`() {
        val periode = Periode.create(januar.fraOgMed, mars.tilOgMed)
        val grunnlag = grunnlag(
            vedtakId = 10,
            registrertTidspunkt = "2020-01-10T10:00:00",
            stønadFraOgMed = februar.fraOgMed,
            stønadTilOgMed = februar.tilOgMed,
        )

        val tidslinje = OriginalHistoriskInfotrygdYtelsestidslinje.bygg(
            projeksjonId = projeksjonId,
            periode = periode,
            grunnlag = listOf(grunnlag),
        )

        tidslinje.måneder.values.map { it::class } shouldBe listOf(
            HistoriskInfotrygdYtelseForMåned.IngenYtelse::class,
            HistoriskInfotrygdYtelseForMåned.Ytelse::class,
            HistoriskInfotrygdYtelseForMåned.IngenYtelse::class,
        )
    }

    @Test
    fun `åpen beløpslinje dekker måneder innenfor vedtaket men ikke etter vedtaket`() {
        val år = 2013
        val februar = februar(år)
        val mars = mars(år)
        val april = april(år)
        val periode = Periode.create(februar.fraOgMed, april.tilOgMed)
        val vedtak = grunnlag(
            vedtakId = 4655362,
            registrertTidspunkt = "2013-01-09T10:24:12",
            fraOgMed = februar.fraOgMed,
            tilOgMed = mars.tilOgMed,
            stønadFraOgMed = LocalDate.of(2012, 4, 1),
            stønadTilOgMed = null,
            beløpFraOgMed = februar.fraOgMed,
            beløpTilOgMed = null,
        )

        val tidslinje = OriginalHistoriskInfotrygdYtelsestidslinje.bygg(
            projeksjonId = projeksjonId,
            periode = periode,
            grunnlag = listOf(vedtak),
        )

        listOf(februar, mars).forEach { måned ->
            (tidslinje.måneder.getValue(måned) as HistoriskInfotrygdYtelseForMåned.Ytelse)
                .vedtakId shouldBe vedtak.vedtak.vedtakId
        }
        tidslinje.måneder.getValue(april) shouldBe
            HistoriskInfotrygdYtelseForMåned.IngenYtelse(
                måned = april,
                årsak = HistoriskInfotrygdIngenYtelseÅrsak.INGEN_GJELDENDE_VEDTAK,
            )
    }

    @Test
    fun `opphør erstatter tidligere ytelse og beholder historisk identitet`() {
        val periode = januar
        val opphørsvedtakId = 11L
        val ytelsesvedtak = grunnlag(
            vedtakId = 10,
            registrertTidspunkt = "2020-01-10T10:00:00",
        )
        val opphørsvedtak = grunnlag(
            vedtakId = opphørsvedtakId,
            registrertTidspunkt = "2020-02-10T10:00:00",
            resultat = HistoriskResultat.OPPHØRT,
        )

        val tidslinje = OriginalHistoriskInfotrygdYtelsestidslinje.bygg(
            projeksjonId = projeksjonId,
            periode = periode,
            grunnlag = listOf(ytelsesvedtak, opphørsvedtak),
        )

        tidslinje.måneder.getValue(periode) shouldBe
            HistoriskInfotrygdYtelseForMåned.IngenYtelse(
                måned = periode,
                årsak = HistoriskInfotrygdIngenYtelseÅrsak.OPPHØRT,
                stønadId = stønadId,
                vedtakId = HistoriskVedtakId(opphørsvedtakId),
                oppdragId = oppdragId,
            )
    }

    private fun grunnlag(
        vedtakId: Long,
        registrertTidspunkt: String,
        resultat: HistoriskResultat = HistoriskResultat.INNVILGET,
        endringskoder: List<String> = emptyList(),
        fraOgMed: LocalDate = januar.fraOgMed,
        tilOgMed: LocalDate = mars.tilOgMed,
        stønadFraOgMed: LocalDate? = januar.fraOgMed,
        stønadTilOgMed: LocalDate? = mars.tilOgMed,
        beløpFraOgMed: LocalDate = fraOgMed,
        beløpTilOgMed: LocalDate? = tilOgMed,
        sats: Int = OriginalHistoriskInfotrygdYtelsestidslinjeTest.sats,
    ) = HistoriskInfotrygdTidslinjegrunnlag(
        vedtak = HistoriskInfotrygdTidslinjevedtak(
            stønadId = stønadId,
            vedtakId = HistoriskVedtakId(vedtakId),
            oppdragId = oppdragId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            resultat = resultat,
            bosituasjon = HistoriskBosituasjon.ENSLIG,
            registrertTidspunkt = LocalDateTime.parse(registrertTidspunkt),
            endringskoder = endringskoder,
        ),
        stønadsavgrensning = HistoriskStønadsavgrensning(
            stønadId = stønadId,
            fraOgMed = stønadFraOgMed,
            tilOgMed = stønadTilOgMed,
        ),
        månedsbeløp = listOf(
            HistoriskInfotrygdBeløpsperiode(
                fraOgMed = beløpFraOgMed,
                tilOgMed = beløpTilOgMed,
                sats = BigDecimal(sats),
                fradrag = fradrag,
                fradragskoder = fradragskoder,
            ),
        ),
    )

    private companion object {
        val år = 2020
        val januar = januar(år)
        val februar = februar(år)
        val mars = mars(år)
        val stønadId = HistoriskStønadId(1)
        val oppdragId = "oppdrag-1"
        val sats = 10_000
        val fradrag = BigDecimal(1_000)
        val fradragskoder = listOf("ARBM")
        val projeksjonId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
