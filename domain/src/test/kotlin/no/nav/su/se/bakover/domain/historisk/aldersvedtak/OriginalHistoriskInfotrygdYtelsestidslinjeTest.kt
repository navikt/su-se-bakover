package no.nav.su.se.bakover.domain.historisk.aldersvedtak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

internal class OriginalHistoriskInfotrygdYtelsestidslinjeTest {
    @Test
    fun `velger senest registrerte vedtak og bruker vedtakId som tie-breaker`() {
        val periode = Periode.create(dato(2020, 1, 1), dato(2020, 3, 31))
        val eldreVedtak = grunnlag(
            vedtakId = 10,
            registrertTidspunkt = "2020-01-10T10:00:00",
            sats = 10_000,
        )
        val nyereVedtak = grunnlag(
            vedtakId = 11,
            registrertTidspunkt = "2020-02-10T10:00:00",
            sats = 11_000,
        )
        val høyereVedtakId = grunnlag(
            vedtakId = 12,
            registrertTidspunkt = "2020-02-10T10:00:00",
            sats = 12_000,
        )

        val tidslinje = OriginalHistoriskInfotrygdYtelsestidslinje.bygg(
            projeksjonId = projeksjonId,
            periode = periode,
            grunnlag = listOf(eldreVedtak, nyereVedtak, høyereVedtakId),
        )

        tidslinje.måneder.values.map {
            (it as HistoriskInfotrygdYtelseForMåned.Ytelse).vedtakId
        } shouldBe listOf(HistoriskVedtakId(12), HistoriskVedtakId(12), HistoriskVedtakId(12))
    }

    @Test
    fun `ignorerer annullerte og uendrede vedtak`() {
        val periode = januar(2020)
        val ytelsesvedtak = grunnlag(vedtakId = 10, registrertTidspunkt = "2020-01-10T10:00:00")
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

        (tidslinje.måneder.getValue(januar(2020)) as HistoriskInfotrygdYtelseForMåned.Ytelse)
            .vedtakId shouldBe HistoriskVedtakId(10)
    }

    @Test
    fun `gir eksplisitt ingen ytelse for hull og manglende månedsbeløp`() {
        val periode = Periode.create(dato(2020, 1, 1), dato(2020, 3, 31))
        val grunnlag = grunnlag(
            vedtakId = 10,
            registrertTidspunkt = "2020-01-10T10:00:00",
            fraOgMed = dato(2020, 2, 1),
            tilOgMed = dato(2020, 3, 31),
            beløpFraOgMed = dato(2020, 2, 1),
            beløpTilOgMed = dato(2020, 2, 29),
        )

        val tidslinje = OriginalHistoriskInfotrygdYtelsestidslinje.bygg(
            projeksjonId = projeksjonId,
            periode = periode,
            grunnlag = listOf(grunnlag),
        )

        tidslinje.måneder.values shouldBe listOf(
            HistoriskInfotrygdYtelseForMåned.IngenYtelse(
                måned = januar(2020),
                årsak = HistoriskInfotrygdIngenYtelseÅrsak.INGEN_GJELDENDE_VEDTAK,
            ),
            HistoriskInfotrygdYtelseForMåned.Ytelse(
                måned = no.nav.su.se.bakover.common.tid.periode.februar(2020),
                stønadId = HistoriskStønadId(1),
                vedtakId = HistoriskVedtakId(10),
                oppdragId = "oppdrag-1",
                linjeId = HistoriskOppdragLinjeId("1"),
                bosituasjon = HistoriskBosituasjon.ENSLIG,
                årligYtelsesbeløp = BigDecimal(120_000),
                sats = BigDecimal(10_000),
                fradrag = BigDecimal(1_000),
                fradragskoder = listOf("ARBM"),
            ),
            HistoriskInfotrygdYtelseForMåned.IngenYtelse(
                måned = mars(2020),
                årsak = HistoriskInfotrygdIngenYtelseÅrsak.MANGLER_MÅNEDSBELØP,
                vedtakId = HistoriskVedtakId(10),
            ),
        )
    }

    @Test
    fun `avgrenser med stønadens periode`() {
        val periode = Periode.create(dato(2020, 1, 1), dato(2020, 3, 31))
        val grunnlag = grunnlag(
            vedtakId = 10,
            registrertTidspunkt = "2020-01-10T10:00:00",
            stønadFraOgMed = dato(2020, 2, 1),
            stønadTilOgMed = dato(2020, 2, 29),
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
    fun `opphør erstatter tidligere ytelse og beholder historisk identitet`() {
        val periode = januar(2020)
        val ytelsesvedtak = grunnlag(
            vedtakId = 10,
            registrertTidspunkt = "2020-01-10T10:00:00",
        )
        val opphørsvedtak = grunnlag(
            vedtakId = 11,
            registrertTidspunkt = "2020-02-10T10:00:00",
            resultat = HistoriskResultat.OPPHØRT,
        )

        val tidslinje = OriginalHistoriskInfotrygdYtelsestidslinje.bygg(
            projeksjonId = projeksjonId,
            periode = periode,
            grunnlag = listOf(ytelsesvedtak, opphørsvedtak),
        )

        tidslinje.måneder.getValue(januar(2020)) shouldBe
            HistoriskInfotrygdYtelseForMåned.IngenYtelse(
                måned = januar(2020),
                årsak = HistoriskInfotrygdIngenYtelseÅrsak.OPPHØRT,
                stønadId = HistoriskStønadId(1),
                vedtakId = HistoriskVedtakId(11),
                oppdragId = "oppdrag-1",
            )
    }

    private fun grunnlag(
        vedtakId: Long,
        registrertTidspunkt: String,
        resultat: HistoriskResultat = HistoriskResultat.INNVILGET,
        endringskoder: List<String> = emptyList(),
        fraOgMed: LocalDate = dato(2020, 1, 1),
        tilOgMed: LocalDate = dato(2020, 3, 31),
        stønadFraOgMed: LocalDate? = dato(2020, 1, 1),
        stønadTilOgMed: LocalDate? = dato(2020, 3, 31),
        beløpFraOgMed: LocalDate = fraOgMed,
        beløpTilOgMed: LocalDate = tilOgMed,
        sats: Int = 10_000,
    ) = HistoriskInfotrygdTidslinjegrunnlag(
        vedtak = HistoriskVedtaksperiode(
            stønadId = HistoriskStønadId(1),
            vedtakId = HistoriskVedtakId(vedtakId),
            oppdragId = "oppdrag-1",
            opphørskodeRaw = null,
            opphørsgrunn = null,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            behandlingstypeRaw = "S",
            behandlingstype = HistoriskBehandlingstype.SØKNAD,
            resultatRaw = "I",
            resultat = resultat,
            bosituasjonRaw = "EN",
            bosituasjon = HistoriskBosituasjon.ENSLIG,
            årligYtelsesbeløp = BigDecimal(120_000),
            revurderingsdato = null,
            registrertTidspunkt = registrertTidspunkt,
            endringskoder = endringskoder,
            saksreferanse = HistoriskSaksreferanse(null, null, null, null),
            sendtTilOs = null,
            mottattFraOs = null,
            godkjentAvOs = null,
        ),
        stønadsavgrensning = HistoriskStønadsavgrensning(
            stønadId = HistoriskStønadId(1),
            fraOgMed = stønadFraOgMed,
            tilOgMed = stønadTilOgMed,
        ),
        månedsbeløp = listOf(
            HistoriskMånedsbeløpsperiode(
                linjeId = HistoriskOppdragLinjeId("1"),
                fraOgMed = beløpFraOgMed,
                tilOgMed = beløpTilOgMed,
                sats = BigDecimal(sats),
                fradrag = BigDecimal(1_000),
                fradragskoder = listOf("ARBM"),
            ),
        ),
    )

    private fun dato(år: Int, måned: Int, dag: Int): LocalDate = LocalDate.of(år, måned, dag)

    private companion object {
        val projeksjonId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
