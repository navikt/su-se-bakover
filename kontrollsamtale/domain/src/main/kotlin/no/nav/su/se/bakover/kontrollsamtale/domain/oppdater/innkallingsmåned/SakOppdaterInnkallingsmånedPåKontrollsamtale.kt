package no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.innkallingsmåned

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.domain.tid.periode.NonEmptyIkkeOverlappendePerioder
import no.nav.su.se.bakover.common.domain.tid.startOfMonth
import no.nav.su.se.bakover.common.tid.periode.toMåned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtaler
import java.time.Clock

fun Sak.oppdaterInnkallingsmånedPåKontrollsamtale(
    command: OppdaterInnkallingsmånedPåKontrollsamtaleCommand,
    kontrollsamtaler: Kontrollsamtaler,
    clock: Clock,
): Either<KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale, Pair<Kontrollsamtale, Kontrollsamtaler>> {
    val stønadsperioder = this.hentInnvilgetStønadsperioder() as? NonEmptyIkkeOverlappendePerioder
        ?: throw IllegalStateException("Kunne ikke oppdatere innkallingsmåned på kontrollsamtale: Fant ingen innvilget stønadsperioder på saken. Command=$command")
    val fraOgMedMåned = stønadsperioder.fraOgMed.toMåned()
    val tilOgMedMåned = stønadsperioder.tilOgMed.startOfMonth().toMåned()
    if (command.nyInnkallingsmåned < fraOgMedMåned || command.nyInnkallingsmåned > tilOgMedMåned) {
        return KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale.InnkallingsmånedUtenforStønadsperiode(
            innkallingsmåned = command.nyInnkallingsmåned,
            stønadsperioder = stønadsperioder,
        ).left()
    }
    return kontrollsamtaler.oppdaterInnkallingsmåned(command, kontrollsamtaler, clock)
}
