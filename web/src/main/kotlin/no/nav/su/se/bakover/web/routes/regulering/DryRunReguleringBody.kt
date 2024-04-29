package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigMåned
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.StartAutomatiskReguleringForInnsynCommand
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import java.time.Clock
import java.time.LocalDate

/**
 * @param fraOgMedMåned Måned i formatet yyyy-MM
 * @param virkningstidspunkt Dato i formatet yyyy-MM-dd, hvis null settes den til fraOgMedMåned
 * @param ikrafttredelse Dato i formatet yyyy-MM-dd, hvis null settes den til virkningstidspunkt
 * @param grunnbeløp Hvis null, bruker vi bare eksisterende verdier
 * @param garantipensjonOrdinær Hvis null, bruker vi bare eksisterende verdier
 * @param garantipensjonHøy Hvis null, bruker vi bare eksisterende verdier
 */
data class DryRunReguleringBody(
    val fraOgMedMåned: String,
    val virkningstidspunkt: String?,
    val ikrafttredelse: String?,
    val csv: String? = null,
    val grunnbeløp: Int? = null,
    val garantipensjonOrdinær: Int? = null,
    val garantipensjonHøy: Int? = null,
) {
    fun toCommand(clock: Clock): Either<Resultat, StartAutomatiskReguleringForInnsynCommand> {
        val parsedFraOgMedMåned = Måned.parse(fraOgMedMåned) ?: return ugyldigMåned.left()
        val parsedVirkningstidspunkt =
            virkningstidspunkt?.let {
                LocalDate.parse(it) ?: return Feilresponser.ugyldigDato.left()
            }
        val parsedIkrafttredelse =
            ikrafttredelse?.let {
                LocalDate.parse(it) ?: return Feilresponser.ugyldigDato.left()
            }
        return StartAutomatiskReguleringForInnsynCommand(
            fraOgMedMåned = parsedFraOgMedMåned,
            virkningstidspunkt = parsedVirkningstidspunkt ?: parsedFraOgMedMåned.fraOgMed,
            ikrafttredelse = parsedIkrafttredelse ?: parsedFraOgMedMåned.fraOgMed,
            grunnbeløp = grunnbeløp,
            garantipensjonOrdinær = garantipensjonOrdinær,
            garantipensjonHøy = garantipensjonHøy,
            supplement = if (csv != null) {
                parseCSVFromString(csv, clock).fold(
                    ifLeft = { return it.left() },
                    ifRight = { it },
                )
            } else {
                Reguleringssupplement.empty(clock)
            },
        ).right()
    }
}
