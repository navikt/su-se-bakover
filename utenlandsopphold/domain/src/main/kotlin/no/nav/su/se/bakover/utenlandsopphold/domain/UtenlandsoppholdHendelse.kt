package no.nav.su.se.bakover.utenlandsopphold.domain

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

/**
 * @property entitetId for utenlandsopphold-hendelser er denne det samme som sakId
 * @property tidligereHendelseId dersom denne hendelsen korrigerer, delvis korrigerer eller annullerer en tidligere hendelse.
 */
interface UtenlandsoppholdHendelse : Hendelse {
    override val hendelseId: HendelseId
    override val tidligereHendelseId: HendelseId?
    override val sakId: UUID
    val utførtAv: NavIdentBruker.Saksbehandler
    override val hendelsestidspunkt: Tidspunkt
    override val versjon: Hendelsesversjon
    override val meta: HendelseMetadata
    override val entitetId: UUID
        get() = sakId

    /**
     * Kan kun sammenligne hendelser innenfor en entitet (har ikke tatt høyde for hendelsesnummer i domenet (enda)).
     */
    override fun compareTo(other: Hendelse): Int {
        require(entitetId == other.entitetId)
        return this.versjon.compareTo(other.versjon)
    }
}
