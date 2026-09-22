package no.nav.su.se.bakover.web.routes.regulering.omregning

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.service.regulering.aldersfradrag.StartAutomatiskOmregningForInnsynCommand

data class DryRunOmregningBody(
    val fraOgMedMåned: String,
    val lagreManuelle: Boolean = false,
    val maksAntallSaker: Int? = null,
    val kunSakstype: String? = null,
) {
    fun toCommand(): Either<Resultat, StartAutomatiskOmregningForInnsynCommand> {
        val måned = Måned.parse(fraOgMedMåned)
            ?: return Feilresponser.ugyldigMåned.left()

        val sakstype = kunSakstype?.let { Sakstype.valueOf(it) }

        return StartAutomatiskOmregningForInnsynCommand(
            fraOgMedMåned = måned,
            lagreManuelle = lagreManuelle,
            maksAntallSaker = maksAntallSaker,
            kunSakstype = sakstype,
        ).right()
    }
}
