package no.nav.su.se.bakover.common.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt

/**
 * Når noe er avsluttet, betyr det at det ikke kan endres. Eksempler kan være når noe er ferdigbehandlet og skal bli låst.
 * Eksempler: [vedtak.domain.Vedtak], iverksatte behandlinger og avbrutte behandlinger.
 */
interface Avsluttet {
    // kan kanskje rename bare til avsluttet
    val avsluttetTidspunkt: Tidspunkt
    val avsluttetAv: NavIdentBruker?

    fun erÅpen(): Boolean = false
    fun erAvsluttet(): Boolean = true

    /**
     * En avbrutt behandling er også avsluttet.
     * Dersom denne er null, klarer vi ikke ta stilling til om den er iverksatt/vedtatt eller avbrutt.
     * TODO jah: I noen tilfeller kan vi ikke avgjøre om en Behandling er iverksatt eller avbrutt. Dette gjelder spesifikt avvis søknad/søknadsbehandling. Som i de fleste tilfeller er/skal være et vedtak/iverksetting, men vi har bare behandlet det som en avbrutt søknad/søknadsbehandling.
     */
    fun erAvbrutt(): Boolean?
}
