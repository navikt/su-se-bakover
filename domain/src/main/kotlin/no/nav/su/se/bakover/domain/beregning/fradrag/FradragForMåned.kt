package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Månedsperiode
import no.nav.su.se.bakover.common.periode.toMånedsperiode
import no.nav.su.se.bakover.domain.CopyArgs

/**
 * Et fradrag for en spesifikk måned.
 * Brukes for eksempel av [no.nav.su.se.bakover.domain.beregning.Månedsberegning]
 * Dersom du trenger et fradrag for en lengre periode, bruk [FradragForPeriode].
 */
data class FradragForMåned(
    override val fradragstype: Fradragstype,
    override val månedsbeløp: Double,
    val måned: Månedsperiode,
    override val utenlandskInntekt: UtenlandskInntekt? = null,
    override val tilhører: FradragTilhører
) : Fradrag {

    init {
        require(månedsbeløp >= 0.0) { "Fradrag kan ikke være negative" }
    }
    override val periode: Månedsperiode = måned

    override fun copy(args: CopyArgs.Snitt): Fradrag? {
        return args.snittFor(periode)?.let { copy(måned = it.toMånedsperiode()) }
    }
}
