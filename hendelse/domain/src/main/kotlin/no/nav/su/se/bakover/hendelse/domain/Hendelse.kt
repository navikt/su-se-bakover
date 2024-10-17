package no.nav.su.se.bakover.hendelse.domain

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

/**
 * @property hendelseId unikt identifiserer denne hendelsen.
 * @property tidligereHendelseId en tidligere hendelse som denne nye hendelsen korrigerer / tillegger / annullerer
 * @property entitetId Også kalt streamId. knytter et domeneområdet sammen (f.eks sak)
 * @property versjon rekkefølgen hendelser skjer innenfor [entitetId]
 * @property hendelsestidspunkt Tidspunktet hendelsen skjedde fra domenet sin side.
 */
interface Hendelse<T : Hendelse<T>> : Comparable<T> {
    val hendelseId: HendelseId
    val tidligereHendelseId: HendelseId?
    val entitetId: UUID
    val versjon: Hendelsesversjon
    val hendelsestidspunkt: Tidspunkt
}
