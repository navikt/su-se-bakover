package no.nav.su.se.bakover.domain.søknad

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.sak.SakRegistrertHendelse
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.time.Clock
import java.util.UUID

/**
 * @param fnr Frontenden registrer første, andre og Nte søknad på et fødselsnummer og ikke en sak. Derfor kan dette teoretisk forandre seg fra søknad til søknad.
 */
data class SøknadRegistrertHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    // TODO jah: Se på denne sammen med fnr i SakRegistrertHendelse
    val fnr: Fnr,
    val registrertAv: NavIdentBruker,
    val søknadsinnhold: SøknadInnhold,
    override val hendelsestidspunkt: Tidspunkt,
    override val meta: HendelseMetadata,
) : Hendelse {

    override val tidligereHendelseId: HendelseId? = null

    override val entitetId: UUID = sakId

    /** SakRegistrert-hendelsen har versjon 1, mens SøknadRegistrert-hendelsen har versjon 2. Etter det er det ikke en bestemt rekkefølge på hendelser, men det er stor sannsynlighet for at det blir en søknad journalført-hendelse og en oppgave-opprettet-hendelse. */
    override val versjon: Hendelsesversjon = Hendelsesversjon(2)

    override fun compareTo(other: Hendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        /** Reservert for persisteringslaget/tester. */
        fun fraPersistert(
            hendelseId: HendelseId,
            sakId: UUID,
            fnr: Fnr,
            opprettetAv: NavIdentBruker,
            hendelsestidspunkt: Tidspunkt,
            meta: HendelseMetadata,
            entitetId: UUID,
            versjon: Hendelsesversjon,
            søknadsinnhold: SøknadInnhold,
        ): SøknadRegistrertHendelse {
            return SøknadRegistrertHendelse(
                hendelseId = hendelseId,
                sakId = sakId,
                fnr = fnr,
                registrertAv = opprettetAv,
                hendelsestidspunkt = hendelsestidspunkt,
                meta = meta,
                søknadsinnhold = søknadsinnhold,
            ).also {
                require(entitetId == it.entitetId) {
                    "EntitetId til SakOpprettetHendelse må være sakId $sakId, men var $entitetId"
                }
                require(versjon == it.versjon) {
                    "Versjonen til en SakOpprettetHendelse må være ${it.versjon}, men var $versjon"
                }
            }
        }

        fun registrer(
            sakRegistrertHendelse: SakRegistrertHendelse,
            hendelseId: HendelseId = HendelseId.generer(),
            clock: Clock,
            hendelsestidspunkt: Tidspunkt = Tidspunkt.now(clock),
            fnr: Fnr,
            registrertAv: NavIdentBruker.Saksbehandler,
            meta: HendelseMetadata,
            søknadsinnhold: SøknadInnhold,
        ): SøknadRegistrertHendelse {
            return SøknadRegistrertHendelse(
                hendelseId = hendelseId,
                sakId = sakRegistrertHendelse.sakId,
                fnr = fnr,
                registrertAv = registrertAv,
                hendelsestidspunkt = hendelsestidspunkt,
                meta = meta,
                søknadsinnhold = søknadsinnhold,
            )
        }
    }
}
