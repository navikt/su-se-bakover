package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigMåned
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.DryRunNyttGrunnbeløp
import no.nav.su.se.bakover.domain.regulering.StartAutomatiskReguleringForInnsynCommand
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import no.nav.su.se.bakover.web.routes.regulering.uttrekk.pesys.parseCSVFromString
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

/**
 * @param virkningstidspunkt Dato i formatet yyyy-MM-dd
 * @param ikrafttredelse Dato i formatet yyyy-MM-dd, hvis null settes den til virkningstidspunkt
 */
data class DryRunGrunnbeløp(
    val virkningstidspunkt: String,
    val ikrafttredelse: String?,
    val grunnbeløp: Int,
    val omregningsfaktor: String,
)

/**
 * @param startDatoRegulering Måned i formatet yyyy-MM - Hvilken måned reguleringen skal startes fra
 * @param gjeldendeSatsFra Dato i formatet yyyy-MM-dd - bestemmer hvilken gjeldende sats som skal brukes
 * @param dryRunGrunnbeløp Settings for kjøring av nytt grunnbeløp - Hvis null, brukes bare eksisterende grunnbeløp i [grunnbeløpsendringer]
 */
data class DryRunReguleringBody(
    val startDatoRegulering: String,
    val gjeldendeSatsFra: String,
    val dryRunGrunnbeløp: DryRunGrunnbeløp?,
    val csv: String? = null,
) {
    fun toCommand(clock: Clock): Either<Resultat, StartAutomatiskReguleringForInnsynCommand> {
        return StartAutomatiskReguleringForInnsynCommand(
            gjeldendeSatsFra = LocalDate.parse(gjeldendeSatsFra),
            startDatoRegulering = Måned.parse(startDatoRegulering) ?: return ugyldigMåned.left(),
            dryRunNyttGrunnbeløp = dryRunGrunnbeløp?.let {
                val parsedVirkningstidspunkt =
                    it.virkningstidspunkt.let { LocalDate.parse(it) ?: return Feilresponser.ugyldigDato.left() }
                val parsedIkrafttredelse =
                    it.ikrafttredelse?.let { LocalDate.parse(it) ?: return Feilresponser.ugyldigDato.left() }
                DryRunNyttGrunnbeløp(
                    virkningstidspunkt = parsedVirkningstidspunkt,
                    ikrafttredelse = parsedIkrafttredelse ?: parsedVirkningstidspunkt,
                    omregningsfaktor = BigDecimal(it.omregningsfaktor),
                    grunnbeløp = it.grunnbeløp,
                )
            },
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
