package no.nav.su.se.bakover.domain.historisk.revurdering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdYtelseForMåned
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskOppdragLinjeId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.OriginalHistoriskInfotrygdYtelsestidslinje
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

internal class GjeldendeHistoriskInfotrygdVedtaksdataTest {
    @Test
    fun `seneste historiske revurderingsvedtak erstatter tidligere resultat per måned`() {
        val førsteVedtakId = HistoriskInfotrygdRevurderingsvedtakId(uuid(10))
        val andreVedtakId = HistoriskInfotrygdRevurderingsvedtakId(uuid(11))
        val original = original()
        val førsteEffekt = HistoriskInfotrygdRevurderingseffekt(
            vedtakId = førsteVedtakId,
            iverksatt = tidspunkt("2020-03-01T10:00:00Z"),
            månedsresultater = linkedMapOf(
                januar(2020) to revurdertYtelse(januar(2020), sats = 11_000),
                februar(2020) to HistoriskInfotrygdRevurdertMånedsresultat.Opphør(
                    måned = februar(2020),
                    opprinneligStønadId = stønadId,
                    opprinneligVedtakId = opprinneligVedtakId,
                    oppdragId = "oppdrag-1",
                    bosituasjon = HistoriskBosituasjon.ENSLIG,
                    sats = BigDecimal(10_000),
                    fradrag = emptyList(),
                ),
            ),
        )
        val andreEffekt = HistoriskInfotrygdRevurderingseffekt(
            vedtakId = andreVedtakId,
            iverksatt = tidspunkt("2020-04-01T10:00:00Z"),
            månedsresultater = linkedMapOf(
                februar(2020) to revurdertYtelse(februar(2020), sats = 12_000),
            ),
        )

        val gjeldende = GjeldendeHistoriskInfotrygdVedtaksdata.bygg(
            original = original,
            effekter = listOf(andreEffekt, førsteEffekt),
        )

        gjeldende.forMåned(januar(2020)) shouldBe GjeldendeHistoriskInfotrygdMånedsdata.Ytelse(
            måned = januar(2020),
            kilde = HistoriskInfotrygdMånedskilde.Revurderingsvedtak(førsteVedtakId),
            opprinneligStønadId = stønadId,
            opprinneligVedtakId = opprinneligVedtakId,
            oppdragId = "oppdrag-1",
            bosituasjon = HistoriskBosituasjon.ENSLIG,
            sats = BigDecimal(11_000),
            fradrag = BigDecimal(1_000),
            fradragsgrunnlag = HistoriskInfotrygdFradragsgrunnlag.RevurderteFradrag(
                fradrag(januar(2020)),
            ),
        )
        gjeldende.forMåned(februar(2020)) shouldBe GjeldendeHistoriskInfotrygdMånedsdata.Ytelse(
            måned = februar(2020),
            kilde = HistoriskInfotrygdMånedskilde.Revurderingsvedtak(andreVedtakId),
            opprinneligStønadId = stønadId,
            opprinneligVedtakId = opprinneligVedtakId,
            oppdragId = "oppdrag-1",
            bosituasjon = HistoriskBosituasjon.ENSLIG,
            sats = BigDecimal(12_000),
            fradrag = BigDecimal(1_000),
            fradragsgrunnlag = HistoriskInfotrygdFradragsgrunnlag.RevurderteFradrag(
                fradrag(februar(2020)),
            ),
        )
    }

    private fun original() = OriginalHistoriskInfotrygdYtelsestidslinje(
        projeksjonId = projeksjonId,
        periode = Periode.create(januar(2020).fraOgMed, februar(2020).tilOgMed),
        måneder = linkedMapOf(
            januar(2020) to originalYtelse(januar(2020)),
            februar(2020) to originalYtelse(februar(2020)),
        ),
    )

    private fun originalYtelse(
        måned: no.nav.su.se.bakover.common.tid.periode.Måned,
    ) = HistoriskInfotrygdYtelseForMåned.Ytelse(
        måned = måned,
        stønadId = stønadId,
        vedtakId = opprinneligVedtakId,
        oppdragId = "oppdrag-1",
        linjeId = HistoriskOppdragLinjeId("1"),
        bosituasjon = HistoriskBosituasjon.ENSLIG,
        årligYtelsesbeløp = BigDecimal(120_000),
        sats = BigDecimal(10_000),
        fradrag = BigDecimal(1_000),
        fradragskoder = listOf("ARBM"),
    )

    private fun revurdertYtelse(
        måned: no.nav.su.se.bakover.common.tid.periode.Måned,
        sats: Int,
    ) = HistoriskInfotrygdRevurdertMånedsresultat.Ytelse(
        måned = måned,
        opprinneligStønadId = stønadId,
        opprinneligVedtakId = opprinneligVedtakId,
        oppdragId = "oppdrag-1",
        bosituasjon = HistoriskBosituasjon.ENSLIG,
        sats = BigDecimal(sats),
        fradrag = fradrag(måned),
    )

    private fun fradrag(
        måned: no.nav.su.se.bakover.common.tid.periode.Måned,
    ): List<FradragForMåned> = listOf(
        FradragForMåned(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 1_000.0,
            måned = måned,
            tilhører = FradragTilhører.BRUKER,
        ),
    )

    private fun tidspunkt(value: String): Tidspunkt = Tidspunkt.create(Instant.parse(value))

    private fun uuid(sisteSiffer: Int): UUID =
        UUID.fromString("00000000-0000-0000-0000-${sisteSiffer.toString().padStart(12, '0')}")

    private companion object {
        val projeksjonId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val stønadId = HistoriskStønadId(1)
        val opprinneligVedtakId = HistoriskVedtakId(2)
    }
}
