package behandling.domain

import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

/**
 * https://jira.adeo.no/browse/BEGREP-201
 */
interface Behandling {
    val id: BehandlingsId
    val opprettet: Tidspunkt
    val sakId: UUID
    val saksnummer: Saksnummer
    val fnr: Fnr

    /** Enten avbrutt eller iverksatt */
    fun erAvsluttet(): Boolean

    /** En avbrutt behandling er også avsluttet.
     * TODO jah: I noen tilfeller kan vi ikke avgjøre om en Behandling er iverksatt eller avbrutt. Dette gjelder spesifikt avvis søknad/søknadsbehandling. Som i de fleste tilfeller er/skal være et vedtak/iverksetting, men vi har bare behandlet det som en avbrutt søknad/søknadsbehandling.
     * */
    fun erAvbrutt(): Boolean?
}
