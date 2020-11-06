package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import java.util.UUID

interface Fradrag : PeriodisertInformasjon {
    fun getId(): UUID
    fun getOpprettet(): Tidspunkt
    fun getFradragstype(): Fradragstype
    fun getTotaltFradrag(): Double
    fun getUtenlandskInntekt(): UtenlandskInntekt? // TODO can we pls do something about this one?
    fun periodiser(): List<Fradrag>
    fun getFradragPerMåned(): Double
}

abstract class AbstractFradrag : Fradrag {
    private val id = UUID.randomUUID()
    private val opprettet = Tidspunkt.now()
    override fun getId(): UUID = id
    override fun getOpprettet() = opprettet
}

internal data class PersistertFradrag(
    private val id: UUID,
    private val opprettet: Tidspunkt,
    private val fradrag: Fradrag
) : AbstractFradrag(), Fradrag by fradrag {
    override fun getId(): UUID = id
    override fun getOpprettet() = opprettet
}
