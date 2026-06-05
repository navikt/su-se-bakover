package no.nav.su.se.bakover.domain.regulering

interface ReguleringRetryService {
    /**
     * Finner alle iverksatte reguleringer der utbetalingen ikke ble sendt til Oppdrag (IBM MQ),
     * og prøver å sende dem på nytt.
     */
    fun retrySendUtbetalingForIkkeOversendte()
}
