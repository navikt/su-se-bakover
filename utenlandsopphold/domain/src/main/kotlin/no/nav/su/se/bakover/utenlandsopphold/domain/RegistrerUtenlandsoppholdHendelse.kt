package no.nav.su.se.bakover.utenlandsopphold.domain

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.periode.DatoIntervall
import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.time.Clock
import java.util.UUID

/**
 * En ny registert utenlandsopphold-hendelse er registrert på en sak.
 *
 * @param registrertUtenlandsoppholdId identifiserer et nytt utenlandsopphold og potensielle endringer på det.
 * @param hendelseId unik id som identifiserer denne hendelsen på tvers av hendelser globalt
 * @property entitetId samme som [sakId] - et utenlandsopphold er knyttet til en sak og andre utenlandsopphold på den saken.
 */
data class RegistrerUtenlandsoppholdHendelse private constructor(
    val registrertUtenlandsoppholdId: UUID,
    override val hendelseId: UUID,
    override val sakId: UUID,
    val periode: DatoIntervall,
    val dokumentasjon: UtenlandsoppholdDokumentasjon,
    val journalposter: List<JournalpostId>,
    val registrertAv: NavIdentBruker.Saksbehandler,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    val erAnnulert: Boolean,
    override val meta: HendelseMetadata,
) : Hendelse {

    override val entitetId: UUID
        get() = sakId

    fun toRegistrertUtenlandsopphold(): RegistrertUtenlandsopphold {
        return RegistrertUtenlandsopphold.fraHendelse(
            utenlandsoppholdId = registrertUtenlandsoppholdId,
            periode = periode,
            dokumentasjon = dokumentasjon,
            journalposter = journalposter,
            opprettetAv = registrertAv,
            opprettetTidspunkt = hendelsestidspunkt,
            endretAv = registrertAv,
            endretTidspunkt = hendelsestidspunkt,
            versjon = versjon,
            erAnnulert = erAnnulert,
        )
    }

    companion object {
        fun registrer(
            registrertUtenlandsoppholdId: UUID = UUID.randomUUID(),
            hendelseId: UUID = UUID.randomUUID(),
            sakId: UUID,
            periode: DatoIntervall,
            dokumentasjon: UtenlandsoppholdDokumentasjon,
            journalposter: List<JournalpostId>,
            opprettetAv: NavIdentBruker.Saksbehandler,
            clock: Clock,
            hendelsestidspunkt: Tidspunkt = Tidspunkt.now(clock),
            hendelseMetadata: HendelseMetadata,
        ): RegistrerUtenlandsoppholdHendelse {
            return RegistrerUtenlandsoppholdHendelse(
                registrertUtenlandsoppholdId = registrertUtenlandsoppholdId,
                hendelseId = hendelseId,
                sakId = sakId,
                periode = periode,
                dokumentasjon = dokumentasjon,
                journalposter = journalposter,
                registrertAv = opprettetAv,
                hendelsestidspunkt = hendelsestidspunkt,
                erAnnulert = false,
                versjon = Hendelsesversjon(1),
                meta = hendelseMetadata,
            )
        }
    }
}
