package no.nav.su.se.bakover.utenlandsopphold.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.util.UUID

/**
 * @property entitetId for utenlandsopphold-hendelser er denne det samme som sakId
 * @property tidligereHendelseId dersom denne hendelsen korrigerer, delvis korrigerer eller annullerer en tidligere hendelse.
 */
interface UtenlandsoppholdHendelse : Sakshendelse {
    override val hendelseId: HendelseId
    override val tidligereHendelseId: HendelseId?
    override val sakId: UUID
    val utførtAv: NavIdentBruker.Saksbehandler
    override val hendelsestidspunkt: Tidspunkt
    override val versjon: Hendelsesversjon
    override val entitetId: UUID
        get() = sakId

    /**
     * Kan kun sammenligne hendelser innenfor en entitet (har ikke tatt høyde for hendelsesnummer i domenet (enda)).
     */
    override fun compareTo(other: Sakshendelse): Int {
        require(entitetId == other.entitetId)
        return this.versjon.compareTo(other.versjon)
    }
}
