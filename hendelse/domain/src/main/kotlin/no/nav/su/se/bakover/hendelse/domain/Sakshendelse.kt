package no.nav.su.se.bakover.hendelse.domain

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

/**
 * @property hendelseId unikt identifiserer denne hendelsen.
 * @property entitetId Også kalt streamId. knytter et domeneområdet sammen (f.eks sak)
 * @property versjon rekkefølgen hendelser skjer innenfor [entitetId]
 * @property hendelsestidspunkt Tidspunktet hendelsen skjedde fra domenet sin side.
 */
interface Sakshendelse : Hendelse<Sakshendelse> {
    override val hendelseId: HendelseId
    override val tidligereHendelseId: HendelseId?
    override val entitetId: UUID
    override val versjon: Hendelsesversjon
    override val hendelsestidspunkt: Tidspunkt
    val sakId: UUID
}
