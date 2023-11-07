package no.nav.su.se.bakover.domain.oppdrag.simulering

import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

/**
 * En sammenligning av utbetalingslinjene mot simuleringen.
 * Hver behandling må forholde seg til forskjellene individuelt.
 * Automatiske behandlinger, bør sjeldnere akseptere forskjeller.
 * Ved manuelle behandlinger, kan saksbehandler/attestant vurdere forskjellene og ta avgjørelsen selv.
 *
 * @property simulertUtbetaling Utbetalingslinjene, inkludert simuleringen
 * @property forskjeller Forskjeller mellom utbetalingslinjene og simuleringen.
 *
 * TODO jah: Er det nærmere domenet og kalle dette avvik istedenfor forskjeller?
 */
sealed interface Simuleringsresultat {

    val simulertUtbetaling: Utbetaling.SimulertUtbetaling
    val forskjeller: ForskjellerMellomUtbetalingOgSimulering?

    data class UtenForskjeller(
        override val simulertUtbetaling: Utbetaling.SimulertUtbetaling,
    ) : Simuleringsresultat {
        override val forskjeller = null
    }

    data class MedForskjeller(
        override val simulertUtbetaling: Utbetaling.SimulertUtbetaling,
        override val forskjeller: ForskjellerMellomUtbetalingOgSimulering,
    ) : Simuleringsresultat
}
