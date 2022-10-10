package no.nav.su.se.bakover.hendelse.application

/**
 * Skal på sikt erstatte [no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver], i hvert fall delvis.
 * Tanken er at vi lager en statistikkjobb som leser fra statistikkhendelsene, persisterer og sender hendelsen.
 */
interface HendelseService {
    fun persisterHendelse()
}
