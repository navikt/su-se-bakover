package no.nav.su.se.bakover.client.oppdrag

import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringResult

interface Simulering {
    fun simulerOppdrag(utbetalingslinjer: Utbetalingslinjer): SimuleringResult
}
