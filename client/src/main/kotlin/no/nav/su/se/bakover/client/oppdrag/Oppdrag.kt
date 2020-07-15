package no.nav.su.se.bakover.client.oppdrag

import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringResult

interface Oppdrag {
    fun simulerOppdrag(utbetalingslinjer: Utbetalingslinjer): SimuleringResult
}
