package no.nav.su.se.bakover.database.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.fixedTidspunkt
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import java.util.UUID

internal object TestBeregning : Beregning {
    private val id = UUID.randomUUID()
    override fun getId(): UUID = id
    override fun getOpprettet(): Tidspunkt = fixedTidspunkt
    override fun getSats(): Sats = Sats.HØY
    override fun getMånedsberegninger(): List<Månedsberegning> = listOf(TestMånedsberegning)
    override fun getFradrag(): List<Fradrag> = listOf(TestFradrag)
    override fun getSumYtelse(): Int = 8637
    override fun getSumFradrag(): Double = 12000.0
    override val periode: Periode = Periode.create(1.januar(2021), 31.januar(2021))
    override fun getFradragStrategyName(): FradragStrategyName = FradragStrategyName.Enslig
    override fun getBegrunnelse(): String? = null
    override fun equals(other: Any?) = (other as? Beregning)?.let { this.equals(other) } ?: false
}

internal object TestMånedsberegning : Månedsberegning {
    override fun getSumYtelse(): Int = 8637
    override fun getSumFradrag(): Double = 12000.0
    override fun getBenyttetGrunnbeløp(): Int = 99858
    override fun getSats(): Sats = Sats.HØY
    override fun getSatsbeløp(): Double = 20637.32
    override fun getFradrag(): List<Fradrag> = listOf(TestFradrag)
    override fun getFribeløpForEps(): Double = 0.0

    override val periode: Periode = Periode.create(1.januar(2021), 31.januar(2021))
    override fun equals(other: Any?) = (other as? Månedsberegning)?.let { this.equals(other) } ?: false
}

internal object TestFradrag : Fradrag {
    override val fradragstype: Fradragstype = Fradragstype.ForventetInntekt
    override val månedsbeløp: Double = 12000.0
    override val utenlandskInntekt: UtenlandskInntekt? = null
    override val tilhører: FradragTilhører = FradragTilhører.BRUKER
    override val periode: Periode = Periode.create(1.januar(2021), 31.januar(2021))
    override fun copy(args: CopyArgs.Snitt): Fradrag? {
        throw NotImplementedError()
    }
}
