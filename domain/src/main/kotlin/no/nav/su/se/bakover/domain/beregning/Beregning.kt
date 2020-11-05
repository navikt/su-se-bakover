package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import java.util.UUID

interface Beregning : PeriodisertInformasjon {
    fun id(): UUID
    fun opprettet(): Tidspunkt
    fun getSats(): Sats
    fun getMånedsberegninger(): List<Månedsberegning>
    fun getFradrag(): List<Fradrag>
    fun getSumYtelse(): Int
    fun getSumFradrag(): Double
    fun getSumYtelse(periode: Periode): Int
    fun getFradrag(periode: Periode): Int
    fun getSumYtelseErUnderMinstebeløp(): Boolean
}

abstract class AbstractBeregning : Beregning {
    private val id by lazy { UUID.randomUUID() }
    private val opprettet by lazy { Tidspunkt.now() }
    override fun id(): UUID = id
    override fun opprettet() = opprettet
}

internal data class PersistertBeregning(
    private val id: UUID,
    private val tidspunkt: Tidspunkt,
    private val beregning: Beregning
) : AbstractBeregning(), Beregning by beregning {
    override fun id(): UUID = id
    override fun opprettet() = tidspunkt
}
