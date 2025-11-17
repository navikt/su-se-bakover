package no.nav.su.se.bakover.common.domain.regelspesifisering

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.Clock

enum class Regelspesifiseringer(
    val kode: String,
    val versjon: String,
) {
    REGEL_UFØRE_FAKTOR("REGEL-UFØRE-FAKTOR", "1"),
    REGEL_BEREGN_SATS_UFØRE_MÅNED("REGEL-BEREGN-SATS-UFØRE-MÅNED", "1"),
    REGEL_MÅNEDSBEREGNING("REGEL-MÅNEDSBEREGNING", "1"),
    ;

    fun benyttRegelspesifisering() = Regelspesifsering(
        kode = this.kode,
        versjon = this.versjon,
        benyttetTidspunkt = Tidspunkt.now(Clock.systemUTC()),
    )
}

data class Regelspesifsering(
    val kode: String,
    val versjon: String,
    val benyttetTidspunkt: Tidspunkt,
)

interface RegelspesifisertBeregning {
    val benyttetRegel: MutableList<Regelspesifsering>
    // TODO grunnlag????
    // TODO kilde ???

    fun leggTilbenyttetRegel(regel: Regelspesifsering): RegelspesifisertBeregning
    fun leggTilbenyttetRegler(regler: List<Regelspesifsering>): RegelspesifisertBeregning
}
