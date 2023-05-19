package no.nav.su.se.bakover.hendelse.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

/**
 * @param hendelseId en unik id som globalt skiller alle hendelser fra hverandre.
 * @param hendelsestidspunkt tidspunktet hendelsen skjedde sett fra domenet sin side. Settes alltid av domenet.
 * @property entitetId tilsvarer sakId for hendelser knyttet direkte til en sak. Vil sammen med versjon være unik.
 * @property versjon Vil alltid være 1 for en sak opprettet hendelse. Vil inkrementeres ved nye hendelser på saken.
 */
// TODO jah: FLytt nærmere sak/sakRepo
data class SakOpprettetHendelse private constructor(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    val fnr: Fnr,
    val opprettetAv: NavIdentBruker,
    override val hendelsestidspunkt: Tidspunkt,
    override val meta: HendelseMetadata,
) : Hendelse {
    override val tidligereHendelseId: HendelseId? = null
    override val entitetId = sakId

    // Dette vil alltid være første versjon i en hendelsesserie for en sak.
    override val versjon = Hendelsesversjon(1L)
    override fun compareTo(other: Hendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        /** Reservert for persisteringslaget/tester. */
        fun fraPersistert(
            hendelseId: HendelseId = HendelseId.generer(),
            sakId: UUID,
            fnr: Fnr,
            opprettetAv: NavIdentBruker,
            hendelsestidspunkt: Tidspunkt,
            meta: HendelseMetadata,
            entitetId: UUID,
            versjon: Long,
        ): SakOpprettetHendelse {
            return SakOpprettetHendelse(
                hendelseId = hendelseId,
                sakId = sakId,
                fnr = fnr,
                opprettetAv = opprettetAv,
                hendelsestidspunkt = hendelsestidspunkt,
                meta = meta,
            ).also {
                require(entitetId == it.entitetId) {
                    "EntitetId til SakOpprettetHendelse må være sakId $sakId, men var $entitetId"
                }
                require(Hendelsesversjon(versjon) == it.versjon) {
                    "Versjonen til en SakOpprettetHendelse må være 1, men var $versjon"
                }
            }
        }
    }
}
