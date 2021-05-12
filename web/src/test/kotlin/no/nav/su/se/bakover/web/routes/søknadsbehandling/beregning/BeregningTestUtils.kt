package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.web.fixedTidspunkt
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneOffset
import java.util.UUID

internal object TestBeregning : Beregning {
    private val id = UUID.randomUUID()
    override fun getId(): UUID = id
    override fun getOpprettet(): Tidspunkt = LocalDateTime.of(2020, Month.AUGUST, 1, 12, 15, 15).toTidspunkt(ZoneOffset.UTC)
    override fun getSats(): Sats = Sats.HØY
    override fun getMånedsberegninger(): List<Månedsberegning> = listOf(TestMånedsberegning)
    override fun getFradrag(): List<Fradrag> = listOf(TestFradrag, TestFradragEps)
    override fun getSumYtelse(): Int = 8637
    override fun getSumFradrag(): Double = 12000.0
    override val periode: Periode = Periode.create(1.august(2020), 31.august(2020))
    override fun getFradragStrategyName(): FradragStrategyName = FradragStrategyName.Enslig
    override fun getBegrunnelse(): String? = null
    override fun equals(other: Any?) = (other as? Beregning)?.let { this.equals(other) } ?: false
}

internal object TestMånedsberegning : Månedsberegning {
    override fun getSumYtelse(): Int = 19946
    override fun getSumFradrag(): Double = 1000.0
    override fun getBenyttetGrunnbeløp(): Int = 101351
    override fun getSats(): Sats = Sats.HØY
    override fun getSatsbeløp(): Double = 20637.32
    override fun getFradrag(): List<Fradrag> = listOf(TestFradrag, TestFradragEps)
    override fun getFribeløpForEps(): Double = 0.0
    override val periode: Periode = Periode.create(1.august(2020), 31.august(2020))
    override fun equals(other: Any?) = (other as? Månedsberegning)?.let { this.equals(other) } ?: false
}

internal object TestFradrag : Fradrag {
    override val opprettet = fixedTidspunkt
    override val fradragstype: Fradragstype = Fradragstype.Arbeidsinntekt
    override val månedsbeløp: Double = 1000.0
    override val utenlandskInntekt: UtenlandskInntekt? = null
    override val tilhører: FradragTilhører = FradragTilhører.BRUKER
    override val periode: Periode = Periode.create(1.august(2020), 31.august(2020))
    override fun copy(args: CopyArgs.Tidslinje) = throw NotImplementedError()
}

internal object TestFradragEps : Fradrag {
    override val opprettet = fixedTidspunkt
    override val fradragstype: Fradragstype = Fradragstype.Arbeidsinntekt
    override val månedsbeløp: Double = 20000.0
    override val utenlandskInntekt: UtenlandskInntekt? = null
    override val tilhører: FradragTilhører = FradragTilhører.EPS
    override val periode: Periode = Periode.create(1.august(2020), 31.august(2020))
    override fun copy(args: CopyArgs.Tidslinje) = throw NotImplementedError()
}
