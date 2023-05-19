package no.nav.su.se.bakover.hendelse.domain

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

/**
 * @property hendelseId unikt identifiserer denne hendelsen.
 * @property entitetId Også kalt streamId. Knytter en serie med hendelser sammen, hvor versjon angir rekkefølgen.
 * @property versjon se: [entitetId]
 * @property hendelsestidspunkt Tidspunktet hendelsen skjedde fra domenet sin side.
 * @property meta metadata knyttet til hendelsen for auditing/tracing/debug-formål.
 */
interface Hendelse : Comparable<Hendelse> {
    val hendelseId: HendelseId
    val tidligereHendelseId: HendelseId?
    val sakId: UUID
    val entitetId: UUID
    val versjon: Hendelsesversjon
    val hendelsestidspunkt: Tidspunkt
    val meta: HendelseMetadata
}
