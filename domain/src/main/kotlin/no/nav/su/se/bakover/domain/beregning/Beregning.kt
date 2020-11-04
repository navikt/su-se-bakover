package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import java.util.UUID

interface Beregning : PeriodisertInformasjon {
    fun id(): UUID
    fun opprettet(): Tidspunkt
    fun sats(): Sats
    fun månedsberegninger(): List<Månedsberegning>
    fun fradrag(): List<Fradrag>
    fun totalSum(): Int
    fun totaltFradrag(): Int
    fun sum(periode: Periode): Int
    fun fradrag(periode: Periode): Int
    fun sumUnderMinstegrense(): Boolean
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
