package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.august
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragForMåned
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.satser.Satskategori
import no.nav.su.se.bakover.test.satsFactoryTest
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneOffset
import java.util.UUID

internal object TestBeregning : Beregning {
    private val id = UUID.randomUUID()
    override fun getId(): UUID = id
    override fun getOpprettet(): Tidspunkt = LocalDateTime.of(2020, Month.AUGUST, 1, 12, 15, 15).toTidspunkt(ZoneOffset.UTC)
    override fun getMånedsberegninger(): List<Månedsberegning> = listOf(TestMånedsberegning)
    override fun getFradrag(): List<Fradrag> = listOf(TestFradrag, TestFradragEps)
    override fun getSumYtelse(): Int = 8637
    override fun getSumFradrag(): Double = 12000.0
    override val periode: Periode = august(2020)
    override fun getBegrunnelse(): String? = null
    override fun equals(other: Any?) = (other as? Beregning)?.let { this.equals(other) } ?: false
}

internal object TestMånedsberegning : Månedsberegning {
    override fun getSumYtelse(): Int = 19946
    override fun getSumFradrag(): Double = 1000.0
    override fun getBenyttetGrunnbeløp(): Int = 101351
    override fun getSats(): Satskategori = Satskategori.HØY
    override fun getSatsbeløp(): Double = 20637.32
    override fun getFradrag(): List<FradragForMåned> = listOf(TestFradrag, TestFradragEps)
    override fun getFribeløpForEps(): Double = 0.0
    override fun getMerknader(): List<Merknad.Beregning> = emptyList()

    override val periode: Periode = august(2020)
    override fun equals(other: Any?) = (other as? Månedsberegning)?.let { this.equals(other) } ?: false
    override val måned: Måned = august(2020)
    override val fullSupplerendeStønadForMåned = satsFactoryTest.høy(august(2021))
}

internal val TestFradrag = FradragForMåned(
    fradragstype = Fradragstype.Arbeidsinntekt,
    månedsbeløp = 1000.0,
    utenlandskInntekt = null,
    tilhører = FradragTilhører.BRUKER,
    måned = august(2020),
)

internal val TestFradragEps = FradragForMåned(
    fradragstype = Fradragstype.Arbeidsinntekt,
    månedsbeløp = 20000.0,
    utenlandskInntekt = null,
    tilhører = FradragTilhører.EPS,
    måned = august(2020),
)
