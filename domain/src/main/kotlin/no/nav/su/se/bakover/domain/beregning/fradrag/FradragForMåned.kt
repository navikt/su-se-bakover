package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Månedsperiode
import no.nav.su.se.bakover.common.periode.toMånedsperiode
import no.nav.su.se.bakover.domain.CopyArgs

internal data class FradragForMåned(
    private val type: FradragskategoriWrapper,
    override val månedsbeløp: Double,
    val måned: Månedsperiode,
    override val utenlandskInntekt: UtenlandskInntekt? = null,
    override val tilhører: FradragTilhører
) : Fradrag {

    init {
        require(månedsbeløp >= 0.0) { "Fradrag kan ikke være negative" }
    }
    override val periode: Månedsperiode = måned

    override val fradragskategoriWrapper: FradragskategoriWrapper = type

    override fun copy(args: CopyArgs.Snitt): Fradrag? {
        return args.snittFor(periode)?.let { copy(måned = it.toMånedsperiode()) }
    }
}
