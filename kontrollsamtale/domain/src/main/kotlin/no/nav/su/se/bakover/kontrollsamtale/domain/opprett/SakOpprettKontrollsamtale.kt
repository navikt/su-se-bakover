package no.nav.su.se.bakover.kontrollsamtale.domain.opprett

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.domain.tid.periode.NonEmptyIkkeOverlappendePerioder
import no.nav.su.se.bakover.common.domain.tid.startOfMonth
import no.nav.su.se.bakover.common.tid.periode.toMåned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtaler
import no.nav.su.se.bakover.kontrollsamtale.domain.regnUtFristFraInnkallingsdato
import java.time.Clock

/**
 * Oppretter en ny, planlagt kontrollsamtale.
 * Frist - se [regnUtFristFraInnkallingsdato]
 */
fun Sak.opprettKontrollsamtale(
    command: OpprettKontrollsamtaleCommand,
    eksisterendeKontrollsamtalerForSak: Kontrollsamtaler,
    clock: Clock,
): Either<KanIkkeOppretteKontrollsamtale, Pair<Kontrollsamtale, Kontrollsamtaler>> {
    val stønadsperioder = this.hentStønadsperioder() as? NonEmptyIkkeOverlappendePerioder ?: return KanIkkeOppretteKontrollsamtale.IngenStønadsperiode.left()
    val fraOgMedMåned = stønadsperioder.fraOgMed.toMåned()
    val tilOgMedMåned = stønadsperioder.tilOgMed.startOfMonth().toMåned()
    if (command.innkallingsmåned < fraOgMedMåned || command.innkallingsmåned > tilOgMedMåned) {
        return KanIkkeOppretteKontrollsamtale.InnkallingsmånedUtenforStønadsperiode(
            innkallingsmåned = command.innkallingsmåned,
            stønadsperioder = stønadsperioder,
        ).left()
    }
    eksisterendeKontrollsamtalerForSak.antallPlanlagteKontrollsamtaler().also {
        if (it > 2) {
            return KanIkkeOppretteKontrollsamtale.MaksAntallPlanlagteKontrollsamtaler(it).left()
        }
    }

    return eksisterendeKontrollsamtalerForSak.opprettKontrollsamtale(command, clock)
}
