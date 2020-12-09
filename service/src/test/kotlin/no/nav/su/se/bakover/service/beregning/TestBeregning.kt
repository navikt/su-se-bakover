package no.nav.su.se.bakover.service.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
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
    private val tidspunkt = Tidspunkt.now()
    override fun getId(): UUID = id
    override fun getOpprettet(): Tidspunkt = tidspunkt
    override fun getSats(): Sats = Sats.HØY
    override fun getMånedsberegninger(): List<Månedsberegning> = listOf(TestMånedsberegning)
    override fun getFradrag(): List<Fradrag> = listOf(TestFradrag)
    override fun getSumYtelse(): Int = 8637
    override fun getSumFradrag(): Double = 12000.0
    override fun getSumYtelseErUnderMinstebeløp(): Boolean = false
    override fun getPeriode(): Periode = Periode(1.januar(2020), 31.januar(2020))
    override fun getFradragStrategyName(): FradragStrategyName = FradragStrategyName.Enslig
}

internal object TestMånedsberegning : Månedsberegning {
    override fun getSumYtelse(): Int = 8637
    override fun getSumFradrag(): Double = 12000.0
    override fun getBenyttetGrunnbeløp(): Int = 99858
    override fun getSats(): Sats = Sats.HØY
    override fun getSatsbeløp(): Double = 20637.32
    override fun getFradrag(): List<Fradrag> = listOf(TestFradrag)
    override fun getPeriode(): Periode = Periode(1.januar(2020), 31.januar(2020))
}

internal object TestFradrag : Fradrag {
    override fun getFradragstype(): Fradragstype = Fradragstype.ForventetInntekt
    override fun getMånedsbeløp(): Double = 12000.0
    override fun getUtenlandskInntekt(): UtenlandskInntekt? = null
    override fun getTilhører(): FradragTilhører = FradragTilhører.BRUKER
    override fun getPeriode(): Periode = Periode(1.januar(2020), 31.januar(2020))
}
