@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Søknadsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.NonEmptyList
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.søknadsbehandling.simuler.KunneIkkeSimulereBehandling
import vilkår.uføre.domain.Uføregrunnlag
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimuleringFeilet
import java.time.Clock

sealed interface KanSimuleres : Søknadsbehandling {
    fun simuler(
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
        simuler: (beregning: Beregning, uføregrunnlag: NonEmptyList<Uføregrunnlag>?) -> Either<SimuleringFeilet, Simulering>,
    ): Either<KunneIkkeSimulereBehandling, SimulertSøknadsbehandling>
}
