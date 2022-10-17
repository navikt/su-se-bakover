package no.nav.su.se.bakover.utenlandsopphold.domain.oppdater

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.periode.DatoIntervall
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrertUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdDokumentasjon
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdHendelse
import java.time.Clock
import java.util.UUID

/**
 *
 *
 * @param forrigeHendelse identifiserer forrige hendelse. Kan være en [RegistrerUtenlandsoppholdHendelse] eller en [OppdaterUtenlandsoppholdHendelse].
 * @param hendelseId unik id som identifiserer denne hendelsen på tvers av hendelser globalt
 * @property entitetId samme som [sakId] - et utenlandsopphold er knyttet til en sak og andre utenlandsopphold på den saken.
 */
data class OppdaterUtenlandsoppholdHendelse private constructor(
    val forrigeHendelse: UtenlandsoppholdHendelse,
    override val hendelseId: UUID,
    override val periode: DatoIntervall,
    override val dokumentasjon: UtenlandsoppholdDokumentasjon,
    override val journalposter: List<JournalpostId>,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val meta: HendelseMetadata,
) : UtenlandsoppholdHendelse {

    override val erAnnullert: Boolean
        get() = forrigeHendelse.erAnnullert

    override val sakId: UUID
        get() = forrigeHendelse.sakId

    override val utenlandsoppholdId: UUID
        get() = forrigeHendelse.utenlandsoppholdId

    override val entitetId: UUID
        get() = forrigeHendelse.entitetId

    fun toRegistrertUtenlandsopphold(): RegistrertUtenlandsopphold {
        return RegistrertUtenlandsopphold.fraHendelse(
            utenlandsoppholdId = utenlandsoppholdId,
            periode = periode,
            dokumentasjon = dokumentasjon,
            journalposter = journalposter,
            opprettetAv = utførtAv,
            opprettetTidspunkt = hendelsestidspunkt,
            endretAv = utførtAv,
            endretTidspunkt = hendelsestidspunkt,
            versjon = versjon,
            erAnnullert = erAnnullert,
        )
    }

    companion object {
        fun create(
            forrigeHendelse: UtenlandsoppholdHendelse,
            hendelseId: UUID = UUID.randomUUID(),
            periode: DatoIntervall,
            dokumentasjon: UtenlandsoppholdDokumentasjon,
            journalposter: List<JournalpostId>,
            utførtAv: NavIdentBruker.Saksbehandler,
            clock: Clock,
            hendelsestidspunkt: Tidspunkt = Tidspunkt.now(clock),
            hendelseMetadata: HendelseMetadata,
            forrigeSakVersjon: Hendelsesversjon,
        ): OppdaterUtenlandsoppholdHendelse {
            // TODO jah: Bør nok legge inn sperre for å oppdatere en annullert hendelse.
            return OppdaterUtenlandsoppholdHendelse(
                forrigeHendelse = forrigeHendelse,
                hendelseId = hendelseId,
                periode = periode,
                dokumentasjon = dokumentasjon,
                journalposter = journalposter,
                utførtAv = utførtAv,
                hendelsestidspunkt = hendelsestidspunkt,
                versjon = forrigeSakVersjon.inc(),
                meta = hendelseMetadata,
            )
        }
    }
}
