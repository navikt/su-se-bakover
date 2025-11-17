package satser.domain.minsteårligytelseforuføretrygdede

import no.nav.su.se.bakover.common.domain.Faktor
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertBeregning
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifsering
import no.nav.su.se.bakover.common.tid.periode.Måned
import satser.domain.Satskategori
import java.math.BigDecimal
import java.time.LocalDate

data class MinsteÅrligYtelseForUføretrygdedeForMåned(
    val faktor: Faktor,
    val satsKategori: Satskategori,
    val ikrafttredelse: LocalDate,
    val virkningstidspunkt: LocalDate,
    val måned: Måned,
    override val benyttetRegel: MutableList<Regelspesifsering>,
) : RegelspesifisertBeregning {
    val faktorSomBigDecimal: BigDecimal = faktor.toBigDecimal()

    override fun leggTilbenyttetRegel(regel: Regelspesifsering): RegelspesifisertBeregning {
        benyttetRegel.add(regel)
        return this
    }

    override fun leggTilbenyttetRegler(regler: List<Regelspesifsering>): RegelspesifisertBeregning {
        benyttetRegel.addAll(regler)
        return this
    }
}
