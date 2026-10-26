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
    val maksAntallSaker: Int? = null,
    val saksnummer: String? = null,
    val sakstype: String? = null,
) {
    fun toCommand(): Either<Resultat, StartAutomatiskOmregningForInnsynCommand> {
        val måned = Måned.parse(fraOgMedMåned)
            ?: return Feilresponser.ugyldigMåned.left()
        val sakstype = sakstype?.let {
            Sakstype.tryFrom(it) ?: return Feilresponser.ugyldigSakstype.left()
        }

        return StartAutomatiskOmregningForInnsynCommand(
            fraOgMedMåned = måned,
            maksAntallSaker = maksAntallSaker,
            saksnummer = saksnummer,
            sakstype = sakstype,
        ).right()
    }
}
