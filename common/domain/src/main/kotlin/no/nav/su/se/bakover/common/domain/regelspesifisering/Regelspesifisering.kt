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
    REGEL_SOSIALSTØNAD_UNDER_2_PROSENT("REGEL-SOSIALSTØNAD-UNDER-2-PROSENT", "1"),
    REGEL_MINDRE_ENN_2_PROSENT("REGEL-MINDRE-ENN-2-PROSENT", "1"),
    REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE("REGEL-TO-PROSENT-AV-HØY-SATS-UFØRE", "1"),
    REGEL_TO_PROSENT_AV_HØY_SATS_ALDER("REGEL-TO-PROSENT-AV-HØY-SATS-ALDER", "1"),
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

// TODO bjg klasse??
interface RegelspesifisertBeregning {
    val benyttetRegel: MutableList<Regelspesifsering>
    // TODO grunnlag????
    // TODO kilde ???

    fun leggTilbenyttetRegel(regel: Regelspesifsering): RegelspesifisertBeregning
    fun leggTilbenyttetRegler(regler: List<Regelspesifsering>): RegelspesifisertBeregning
}
