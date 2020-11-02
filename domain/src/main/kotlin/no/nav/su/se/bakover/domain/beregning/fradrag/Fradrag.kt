package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import no.nav.su.se.bakover.domain.beregning.UtenlandskInntekt
import java.util.UUID

interface IFradrag : PeriodisertInformasjon {
    fun id(): UUID
    fun opprettet(): Tidspunkt
    fun type(): Fradragstype
    fun totalBeløp(): Double
    fun utenlandskInntekt(): UtenlandskInntekt? // TODO can we pls do something about this one?
    fun periodiser(): List<IFradrag>
    fun månedsbeløp(): Double
}

abstract class AbstractFradrag : IFradrag {
    private val id by lazy { UUID.randomUUID() }
    private val opprettet by lazy { Tidspunkt.now() }
    override fun id(): UUID = id
    override fun opprettet() = opprettet
}

internal data class Fradrag(
    private val type: Fradragstype,
    private val beløp: Double,
    private val periode: Periode,
    private val utenlandskInntekt: UtenlandskInntekt? = null
) : AbstractFradrag() {
    init {
        require(beløp >= 0) { "Fradrag kan ikke være negative" }
    }

    override fun månedsbeløp() = beløp / periode.antallMåneder()
    override fun type(): Fradragstype = type
    override fun totalBeløp(): Double = beløp
    override fun utenlandskInntekt(): UtenlandskInntekt? = utenlandskInntekt

    override fun periode(): Periode = periode

    override fun periodiser(): List<IFradrag> = periode.periodiserMåneder()
        .map { this.copy(type = type, beløp = månedsbeløp(), periode = it) }
}

data class FradragDbWrapper(
    private val id: UUID,
    private val tidspunkt: Tidspunkt,
    internal val fradrag: IFradrag
) : AbstractFradrag(), IFradrag by fradrag {
    override fun id(): UUID = id
    override fun opprettet() = tidspunkt
}
