package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import java.util.UUID

interface Fradrag : PeriodisertInformasjon {
    fun id(): UUID
    fun opprettet(): Tidspunkt
    fun type(): Fradragstype
    fun totalBeløp(): Double
    fun utenlandskInntekt(): UtenlandskInntekt? // TODO can we pls do something about this one?
    fun periodiser(): List<Fradrag>
    fun månedsbeløp(): Double
}

abstract class AbstractFradrag : Fradrag {
    private val id by lazy { UUID.randomUUID() }
    private val opprettet by lazy { Tidspunkt.now() }
    override fun id(): UUID = id
    override fun opprettet() = opprettet
}

internal data class PersistertFradrag(
    private val id: UUID,
    private val tidspunkt: Tidspunkt,
    private val fradrag: Fradrag
) : AbstractFradrag(), Fradrag by fradrag {
    override fun id(): UUID = id
    override fun opprettet() = tidspunkt
}
