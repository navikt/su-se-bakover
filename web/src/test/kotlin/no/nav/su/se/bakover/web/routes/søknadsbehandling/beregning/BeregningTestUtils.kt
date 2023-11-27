package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import behandling.domain.beregning.Merknad
import behandling.domain.beregning.fradrag.Fradrag
import behandling.domain.beregning.fradrag.FradragForMåned
import behandling.domain.beregning.fradrag.FradragTilhører
import behandling.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.toTidspunkt
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import sats.Satskategori
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneOffset
import java.util.UUID

internal data object TestBeregning : Beregning {
    private val id = UUID.randomUUID()
    override fun getId(): UUID = id
    override fun getOpprettet(): Tidspunkt = LocalDateTime.of(2020, Month.AUGUST, 1, 12, 15, 15).toTidspunkt(ZoneOffset.UTC)
    override fun getMånedsberegninger(): List<Månedsberegning> = listOf(TestMånedsberegning)
    override fun getFradrag(): List<Fradrag> = listOf(TestFradrag, TestFradragEps)
    override fun getSumYtelse(): Int = 8637
    override fun getSumFradrag(): Double = 12000.0
    override val periode: Periode = august(2020)
    override fun getBegrunnelse(): String? = null
}

internal data object TestMånedsberegning : Månedsberegning {
    override fun getSumYtelse(): Int = 19946
    override fun getSumFradrag(): Double = 1000.0
    override fun getBenyttetGrunnbeløp(): Int = 101351
    override fun getSats(): Satskategori = Satskategori.HØY
    override fun getSatsbeløp(): Double = 20637.32
    override fun getFradrag(): List<FradragForMåned> = listOf(TestFradrag, TestFradragEps)
    override fun getFribeløpForEps(): Double = 0.0
    override fun getMerknader(): List<Merknad.Beregning> = emptyList()

    override val periode: Periode = august(2020)
    override val måned: Måned = august(2020)
    override val fullSupplerendeStønadForMåned = satsFactoryTestPåDato().høyUføre(august(2021))
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
