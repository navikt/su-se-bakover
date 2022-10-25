package no.nav.su.se.bakover.domain.sak

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.time.Clock
import java.util.UUID

/**
 * @param hendelseId en unik id som globalt skiller alle hendelser fra hverandre.
 * @param hendelsestidspunkt tidspunktet hendelsen skjedde sett fra domenet sin side. Settes alltid av domenet.
 * @property entitetId tilsvarer sakId for hendelser knyttet direkte til en sak. Vil sammen med versjon være unik.
 * @property versjon Vil alltid være 1 for en sak opprettet hendelse. Vil inkrementeres ved nye hendelser på saken.
 */
data class SakRegistrertHendelse private constructor(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    val saksnummer: Saksnummer,
    val sakstype: Sakstype,
    val fnr: Fnr,
    // TODO jah: Mulighet til å lagre mer. Vi får innsendt et fnr og gjør et oppslag mot PDL hvor vi får en liste med fnr som har tilhørt personen (og det aktive).

    val registrertAv: NavIdentBruker,
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
        /**
         * Henter en persistert hendelse fra persisteringslaget.
         * Reservert for persisteringslaget/tester.
         */
        fun fraPersistert(
            hendelseId: HendelseId,
            sakId: UUID,
            fnr: Fnr,
            opprettetAv: NavIdentBruker,
            hendelsestidspunkt: Tidspunkt,
            meta: HendelseMetadata,
            entitetId: UUID,
            versjon: Hendelsesversjon,
            type: Sakstype,
            saksnummer: Saksnummer,
        ): SakRegistrertHendelse {
            return SakRegistrertHendelse(
                hendelseId = hendelseId,
                sakId = sakId,
                fnr = fnr,
                registrertAv = opprettetAv,
                hendelsestidspunkt = hendelsestidspunkt,
                meta = meta,
                sakstype = type,
                saksnummer = saksnummer,
            ).also {
                require(entitetId == it.entitetId) {
                    "EntitetId til SakOpprettetHendelse må være sakId $sakId, men var $entitetId"
                }
                require(versjon == it.versjon) {
                    "Versjonen til en SakOpprettetHendelse må være ${it.versjon}, men var $versjon"
                }
            }
        }

        /**
         * Registrer en ny sak (fra domenet)
         */
        fun registrer(
            hendelseId: HendelseId = HendelseId.generer(),
            sakId:UUID,
            fnr: Fnr,
            registrertAv: NavIdentBruker.Saksbehandler,
            clock: Clock,
            hendelsestidspunkt: Tidspunkt = Tidspunkt.now(clock),
            meta: HendelseMetadata,
            sakstype: Sakstype,
            saksnummer: Saksnummer,
        ): SakRegistrertHendelse {
            return SakRegistrertHendelse(
                hendelseId = hendelseId,
                sakId = sakId,
                saksnummer = saksnummer,
                sakstype = sakstype,
                fnr = fnr,
                registrertAv = registrertAv,
                hendelsestidspunkt = hendelsestidspunkt,
                meta = meta,
            )
        }
    }
}
