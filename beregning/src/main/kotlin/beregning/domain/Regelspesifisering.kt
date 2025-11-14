package beregning.domain

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.Clock

enum class Regelspesifiseringer(
    val kode: String,
    val versjon: String,
) {
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
    val benyttetRegel: Regelspesifsering
}
