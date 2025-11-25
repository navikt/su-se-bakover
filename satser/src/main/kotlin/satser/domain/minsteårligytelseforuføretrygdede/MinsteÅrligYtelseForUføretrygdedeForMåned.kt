package satser.domain.minsteårligytelseforuføretrygdede

import no.nav.su.se.bakover.common.domain.Faktor
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertBeregning
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertGrunnlag
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
    override val benyttetRegel: Regelspesifisering = when (satsKategori) {
        // TODO skille mellom uføre og alder...
        Satskategori.ORDINÆR -> RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_ORDINÆR.benyttGrunnlag()
        Satskategori.HØY -> RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_HØY.benyttGrunnlag()
    },
) : RegelspesifisertBeregning {
    val faktorSomBigDecimal: BigDecimal = faktor.toBigDecimal()
}
