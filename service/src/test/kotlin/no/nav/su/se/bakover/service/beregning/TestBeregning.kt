package no.nav.su.se.bakover.service.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragForMåned
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.satser.Satskategori
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import java.util.UUID

internal object TestBeregning : Beregning {
    private val id = UUID.randomUUID()
    private val tidspunkt = fixedTidspunkt
    override fun getId(): UUID = id
    override fun getOpprettet(): Tidspunkt = tidspunkt
    override fun getMånedsberegninger(): List<Månedsberegning> = listOf(TestMånedsberegning)
    override fun getFradrag(): List<Fradrag> = listOf(TestFradrag)
    override fun getSumYtelse(): Int = 8637
    override fun getSumFradrag(): Double = 12000.0
    override val periode: Periode = januar(2020)
    override fun getBegrunnelse(): String? = null
    override fun equals(other: Any?) = (other as? Beregning)?.let { this.equals(other) } ?: false
}

internal object TestMånedsberegning : Månedsberegning {
    override fun getSumYtelse(): Int = 8637
    override fun getSumFradrag(): Double = 12000.0
    override fun getBenyttetGrunnbeløp(): Int = 99858
    override fun getSats(): Satskategori = Satskategori.HØY
    override fun getSatsbeløp(): Double = 20637.32
    override fun getFradrag(): List<FradragForMåned> = listOf(TestFradrag)
    override fun getFribeløpForEps(): Double = 0.0
    override fun getMerknader(): List<Merknad.Beregning> = emptyList()
    override val fullSupplerendeStønadForMåned = satsFactoryTestPåDato().høyUføre(januar(2021))
    override val periode: Periode = januar(2020)
    override fun equals(other: Any?) = (other as? Månedsberegning)?.let { this.equals(other) } ?: false
    override val måned: Måned = januar(2020)
}

val TestFradrag = FradragForMåned(
    fradragstype = Fradragstype.ForventetInntekt,
    månedsbeløp = 12000.0,
    utenlandskInntekt = null,
    tilhører = FradragTilhører.BRUKER,
    måned = januar(2020),
)
