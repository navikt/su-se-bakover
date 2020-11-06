package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import java.util.UUID

interface Beregning : PeriodisertInformasjon {
    fun getId(): UUID
    fun getOpprettet(): Tidspunkt
    fun getSats(): Sats
    fun getMånedsberegninger(): List<Månedsberegning>
    fun getFradrag(): List<Fradrag>
    fun getSumYtelse(): Int
    fun getSumFradrag(): Double
    fun getSumYtelseErUnderMinstebeløp(): Boolean
}

abstract class AbstractBeregning : Beregning {
    private val id = UUID.randomUUID()
    private val opprettet = Tidspunkt.now()
    override fun getId(): UUID = id
    override fun getOpprettet() = opprettet
}

internal data class PersistertBeregning(
    private val id: UUID,
    private val opprettet: Tidspunkt,
    private val beregning: Beregning
) : AbstractBeregning(), Beregning by beregning {
    override fun getId(): UUID = id
    override fun getOpprettet() = opprettet
}
