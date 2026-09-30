package no.nav.su.se.bakover.domain.historisk.revurdering

import behandling.revurdering.domain.Opphørsgrunn
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import org.junit.jupiter.api.Test
import satser.domain.historisk.HistoriskInfotrygdSatskategori
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.util.UUID

internal class BeregnHistoriskInfotrygdRevurderingTest {
    @Test
    fun `beregner ytelse med komplett regeltre`() {
        val beregning = gjeldende().beregnRevurdering(
            listOf(
                grunnlag(januar),
                grunnlag(februar),
            ),
        ).shouldBeRight()

        beregning.månedsresultater.values.map {
            (it as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse).sats
        } shouldBe listOf(forventetMånedssats, forventetMånedssats)

        val hovedregel = beregning.benyttetRegel as Regelspesifisering.Beregning
        hovedregel.kode shouldBe Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_BEREGNING.kode
        hovedregel.avhengigeRegler.size shouldBe 2
        hovedregel.avhengigeRegler.forEach { månedsregel ->
            månedsregel as Regelspesifisering.Beregning
            månedsregel.kode shouldBe Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MÅNEDSBEREGNING.kode
            månedsregel.avhengigeRegler.size shouldBe 3
            (månedsregel.avhengigeRegler[2] as Regelspesifisering.Beregning).kode shouldBe
                Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MINSTEGRENSE.kode
        }
    }

    @Test
    fun `opphører automatisk når beløpet er under minstegrensen`() {
        val beregning = gjeldende().beregnRevurdering(
            listOf(
                grunnlag(januar, fradragUnderMinstegrensen(januar)),
                grunnlag(februar, fradragUnderMinstegrensen(februar)),
            ),
        ).shouldBeRight()

        beregning.månedsresultater.getValue(februar) shouldBe
            HistoriskInfotrygdRevurdertMånedsresultat.Opphør(
                måned = februar,
                opprinneligStønadId = STØNAD_ID,
                opprinneligVedtakId = VEDTAK_ID,
                oppdragId = OPPDRAG_ID,
                bosituasjon = HistoriskBosituasjon.ENSLIG,
                sats = forventetMånedssats,
                fradrag = listOf(fradragUnderMinstegrensen(februar)),
                opphørsgrunn = Opphørsgrunn.SU_UNDER_MINSTEGRENSE,
                manueltOpphør = false,
            )
    }

    @Test
    fun `manuelt opphør gir opphør med valgt opphørsgrunn`() {
        val manueltOpphør = HistoriskInfotrygdManueltOpphør(Opphørsgrunn.FORMUE)
        val beregning = gjeldende().beregnRevurdering(
            listOf(
                grunnlag(januar).copy(manueltOpphør = manueltOpphør),
                grunnlag(februar).copy(manueltOpphør = manueltOpphør),
            ),
        ).shouldBeRight()

        beregning.månedsresultater.values.forEach {
            it as HistoriskInfotrygdRevurdertMånedsresultat.Opphør
            it.opphørsgrunn shouldBe Opphørsgrunn.FORMUE
            it.manueltOpphør shouldBe true
        }
    }

    @Test
    fun `avviser ytelse og opphør i samme revurdering`() {
        gjeldende().beregnRevurdering(
            listOf(
                grunnlag(januar).copy(manueltOpphør = HistoriskInfotrygdManueltOpphør(Opphørsgrunn.FORMUE)),
                grunnlag(februar),
            ),
        ).shouldBeLeft() shouldBe KunneIkkeBeregneHistoriskInfotrygdRevurdering.BlandetYtelseOgOpphør
    }

    @Test
    fun `enslig får ikke fradrag for EPS`() {
        val beregning = gjeldende().beregnRevurdering(
            listOf(
                grunnlag(januar, epsFradrag(januar, Fradragstype.Arbeidsinntekt, 20_000.0)),
                grunnlag(februar, epsFradrag(februar, Fradragstype.Arbeidsinntekt, 20_000.0)),
            ),
        ).shouldBeRight()

        beregning.erOpphør shouldBe false
    }

    @Test
    fun `EPS over 67 trekkes bare for inntekt over ordinær sats i tillegg til sosialstønad`() {
        // EO mai 2019: 181 908 / 12 = 15 159
        val epsSats = BigDecimal(15_159)
        val beregning = gjeldende().beregnRevurdering(
            listOf(januar, februar).map { måned ->
                grunnlag(
                    måned,
                    epsFradrag(måned, Fradragstype.Arbeidsinntekt, 15_659.0),
                    epsFradrag(måned, Fradragstype.Sosialstønad, 100.0),
                ).copy(satskategori = HistoriskInfotrygdSatskategori.EO)
            },
        ).shouldBeRight()

        beregning.månedsresultater.values.forEach {
            it as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse
            it.sats shouldBe epsSats
            it.beløp shouldBe epsSats - BigDecimal(600)
        }
    }

    @Test
    fun `EPS under 67 trekkes fullt ut`() {
        val beregning = gjeldende().beregnRevurdering(
            listOf(januar, februar).map { måned ->
                grunnlag(måned, epsFradrag(måned, Fradragstype.Arbeidsinntekt, 1_000.0))
                    .copy(satskategori = HistoriskInfotrygdSatskategori.EU)
            },
        ).shouldBeRight()

        beregning.månedsresultater.values.forEach {
            it as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse
            it.beløp shouldBe it.sats - BigDecimal(1_000)
        }
    }

    private fun epsFradrag(måned: Måned, fradragstype: Fradragstype, beløp: Double) = FradragForMåned(
        fradragstype = fradragstype,
        månedsbeløp = beløp,
        måned = måned,
        tilhører = FradragTilhører.EPS,
    )

    private fun gjeldende() = GjeldendeHistoriskInfotrygdVedtaksdata(
        projeksjonId = UUID.randomUUID(),
        periode = Periode.create(januar.fraOgMed, februar.tilOgMed),
        tidslinje = linkedMapOf(
            januar to gammelYtelse(januar),
            februar to gammelYtelse(februar),
        ),
    )

    private fun grunnlag(
        måned: Måned,
        vararg fradrag: FradragForMåned,
    ) = HistoriskInfotrygdBeregningsgrunnlagForMåned(
        måned = måned,
        satskategori = HistoriskInfotrygdSatskategori.EN,
        fradrag = fradrag.toList(),
    )

    private fun fradragUnderMinstegrensen(måned: Måned) = FradragForMåned(
        fradragstype = Fradragstype.Arbeidsinntekt,
        månedsbeløp = 15_900.0,
        måned = måned,
        tilhører = FradragTilhører.BRUKER,
    )

    private fun gammelYtelse(måned: Måned) =
        GjeldendeHistoriskInfotrygdMånedsdata.Ytelse(
            måned = måned,
            kilde = HistoriskInfotrygdMånedskilde.OriginalProjeksjon(UUID.randomUUID()),
            opprinneligStønadId = STØNAD_ID,
            opprinneligVedtakId = VEDTAK_ID,
            oppdragId = OPPDRAG_ID,
            bosituasjon = HistoriskBosituasjon.ENSLIG,
            sats = BigDecimal(10_000),
            fradrag = BigDecimal.ZERO,
            fradragsgrunnlag = HistoriskInfotrygdFradragsgrunnlag.OriginaleKoder(emptyList()),
        )

    private companion object {
        val januar = januar(2020)
        val februar = februar(2020)

        // 191 422 / 12 = 15 951,83, avrundet til hele kroner
        val forventetMånedssats: BigDecimal = BigDecimal(15_952)
        val STØNAD_ID = HistoriskStønadId(1)
        val VEDTAK_ID = HistoriskVedtakId(2)
        const val OPPDRAG_ID = "oppdrag-1"
    }
}
