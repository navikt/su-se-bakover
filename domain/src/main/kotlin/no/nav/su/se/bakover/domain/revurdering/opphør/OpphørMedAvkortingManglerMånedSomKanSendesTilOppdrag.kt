package no.nav.su.se.bakover.domain.revurdering.opphør

import no.nav.su.se.bakover.common.periode.Periode
import java.time.LocalDate

/**
 * Se javadoc til [OpphørsperiodeForUtbetalinger].
 * Dersom opphøret inneholder en eller flere måneder med avkorting, krever vi at det finnes en senere ikke-utbetalt måned som også revurderes.
 * Det kan finnes historiske grunner til dette, men sikker grunn er at vi ikke støtter opphør uten utbetalingslinjer.
 * Vi kan ikke sende avkortingsmånedene til oppdrag.
 * Derfor må noen av månedene i revurderingsperioden kunne sendes til oppdrag.
 *
 * @param revurderinsperiode Revurderingsperioden i sin helhet, slik som saksbehandler definerer den, uavhengig av hvilke måneder som fører til avkorting.
 * @param tidligsteIkkeUtbetalteMånedEtterAvkortingsperiode Tidligste ikke-utbetalte måned etter avkortingsmånedene. Denne kan være en måned etter revurderingsperioden og det er i de tilfellene denne feilen oppstår.
 */
data class OpphørMedAvkortingManglerMånedSomKanSendesTilOppdrag(
    val revurderinsperiode: Periode,
    val tidligsteIkkeUtbetalteMånedEtterAvkortingsperiode: LocalDate,
)
