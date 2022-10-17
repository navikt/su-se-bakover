package no.nav.su.se.bakover.utenlandsopphold.domain.registrer

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.periode.DatoIntervall
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrertUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdDokumentasjon
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdHendelse
import java.time.Clock
import java.util.UUID

/**
 * En ny registert utenlandsopphold-hendelse er registrert på en sak.
 *
 * @param utenlandsoppholdId identifiserer et nytt utenlandsopphold og potensielle endringer på det.
 * @param hendelseId unik id som identifiserer denne hendelsen på tvers av hendelser globalt
 * @property entitetId samme som [sakId] - et utenlandsopphold er knyttet til en sak og andre utenlandsopphold på den saken.
 */
data class RegistrerUtenlandsoppholdHendelse private constructor(
    override val utenlandsoppholdId: UUID,
    override val hendelseId: UUID,
    override val sakId: UUID,
    override val periode: DatoIntervall,
    override val dokumentasjon: UtenlandsoppholdDokumentasjon,
    override val journalposter: List<JournalpostId>,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val meta: HendelseMetadata,
) : UtenlandsoppholdHendelse {

    override val erAnnullert: Boolean = false
    override val entitetId: UUID
        get() = sakId

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
        fun registrer(
            utenlandsoppholdId: UUID = UUID.randomUUID(),
            hendelseId: UUID = UUID.randomUUID(),
            sakId: UUID,
            periode: DatoIntervall,
            dokumentasjon: UtenlandsoppholdDokumentasjon,
            journalposter: List<JournalpostId>,
            opprettetAv: NavIdentBruker.Saksbehandler,
            clock: Clock,
            hendelsestidspunkt: Tidspunkt = Tidspunkt.now(clock),
            hendelseMetadata: HendelseMetadata,
            forrigeVersjon: Hendelsesversjon,
        ): RegistrerUtenlandsoppholdHendelse {
            return RegistrerUtenlandsoppholdHendelse(
                utenlandsoppholdId = utenlandsoppholdId,
                hendelseId = hendelseId,
                sakId = sakId,
                periode = periode,
                dokumentasjon = dokumentasjon,
                journalposter = journalposter,
                utførtAv = opprettetAv,
                hendelsestidspunkt = hendelsestidspunkt,
                versjon = forrigeVersjon.inc(),
                meta = hendelseMetadata,
            )
        }

        fun fraPersistert(
            utenlandsoppholdId: UUID,
            hendelseId: UUID,
            sakId: UUID,
            periode: DatoIntervall,
            dokumentasjon: UtenlandsoppholdDokumentasjon,
            journalposter: List<JournalpostId>,
            opprettetAv: NavIdentBruker.Saksbehandler,
            hendelsestidspunkt: Tidspunkt,
            hendelseMetadata: HendelseMetadata,
            forrigeVersjon: Hendelsesversjon,
        ): RegistrerUtenlandsoppholdHendelse {
            return RegistrerUtenlandsoppholdHendelse(
                utenlandsoppholdId = utenlandsoppholdId,
                hendelseId = hendelseId,
                sakId = sakId,
                periode = periode,
                dokumentasjon = dokumentasjon,
                journalposter = journalposter,
                utførtAv = opprettetAv,
                hendelsestidspunkt = hendelsestidspunkt,
                versjon = forrigeVersjon,
                meta = hendelseMetadata,
            )
        }
    }
}
