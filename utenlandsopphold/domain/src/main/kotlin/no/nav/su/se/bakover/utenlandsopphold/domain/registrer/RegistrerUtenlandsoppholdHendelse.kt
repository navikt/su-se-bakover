package no.nav.su.se.bakover.utenlandsopphold.domain.registrer

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.periode.DatoIntervall
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrertUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdDokumentasjon
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.annuller.AnnullerUtenlandsoppholdHendelse
import java.time.Clock
import java.util.UUID

/**
 * En ny registert utenlandsopphold-hendelse er registrert på en sak.
 *
 * @param hendelseId unik id som identifiserer denne hendelsen på tvers av hendelser globalt
 * @property entitetId samme som [sakId] - et utenlandsopphold er knyttet til en sak og andre utenlandsopphold på den saken.
 */
data class RegistrerUtenlandsoppholdHendelse private constructor(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    val periode: DatoIntervall,
    val dokumentasjon: UtenlandsoppholdDokumentasjon,
    val journalposter: List<JournalpostId>,
    val begrunnelse: String?,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val meta: HendelseMetadata,
) : UtenlandsoppholdHendelse {

    override val tidligereHendelseId: HendelseId? = null

    override val entitetId: UUID
        get() = sakId

    fun toRegistrertUtenlandsopphold(): RegistrertUtenlandsopphold {
        return RegistrertUtenlandsopphold.create(
            periode = periode,
            dokumentasjon = dokumentasjon,
            journalposter = journalposter,
            opprettetAv = utførtAv,
            begrunnelse = begrunnelse,
            opprettetTidspunkt = hendelsestidspunkt,
            endretAv = utførtAv,
            endretTidspunkt = hendelsestidspunkt,
            versjon = versjon,
            erAnnullert = false,
        )
    }

    fun toAuditEvent(berørtBrukerId: Fnr): AuditLogEvent {
        return AuditLogEvent(
            navIdent = this.meta.ident.toString(),
            berørtBrukerId = berørtBrukerId,
            action = AuditLogEvent.Action.CREATE,
            // Et utenlandsopphold er ikke knyttet til en behandling, men en sak.
            behandlingId = null,
            callId = this.meta.correlationId?.toString(),
        )
    }

    companion object {
        fun registrer(
            hendelseId: HendelseId = HendelseId.generer(),
            sakId: UUID,
            periode: DatoIntervall,
            dokumentasjon: UtenlandsoppholdDokumentasjon,
            journalposter: List<JournalpostId>,
            begrunnelse: String?,
            opprettetAv: NavIdentBruker.Saksbehandler,
            clock: Clock,
            hendelsestidspunkt: Tidspunkt = Tidspunkt.now(clock),
            hendelseMetadata: HendelseMetadata,
            nesteVersjon: Hendelsesversjon,
        ): RegistrerUtenlandsoppholdHendelse {
            return RegistrerUtenlandsoppholdHendelse(
                hendelseId = hendelseId,
                sakId = sakId,
                periode = periode,
                dokumentasjon = dokumentasjon,
                journalposter = journalposter,
                begrunnelse = begrunnelse,
                utførtAv = opprettetAv,
                hendelsestidspunkt = hendelsestidspunkt,
                versjon = nesteVersjon,
                meta = hendelseMetadata,
            )
        }

        fun fraPersistert(
            hendelseId: HendelseId,
            sakId: UUID,
            periode: DatoIntervall,
            dokumentasjon: UtenlandsoppholdDokumentasjon,
            journalposter: List<JournalpostId>,
            begrunnelse: String?,
            opprettetAv: NavIdentBruker.Saksbehandler,
            hendelsestidspunkt: Tidspunkt,
            hendelseMetadata: HendelseMetadata,
            forrigeVersjon: Hendelsesversjon,
            entitetId: UUID,
        ): RegistrerUtenlandsoppholdHendelse {
            return RegistrerUtenlandsoppholdHendelse(
                hendelseId = hendelseId,
                sakId = sakId,
                periode = periode,
                dokumentasjon = dokumentasjon,
                journalposter = journalposter,
                begrunnelse = begrunnelse,
                utførtAv = opprettetAv,
                hendelsestidspunkt = hendelsestidspunkt,
                versjon = forrigeVersjon,
                meta = hendelseMetadata,
            ).also {
                require(it.entitetId == entitetId) {
                    "Den persistert entitetId var ulik den utleda fra domenet:${it.entitetId} vs. $entitetId. "
                }
            }
        }
    }
}

fun RegistrertUtenlandsopphold.leggTil(hendelse: AnnullerUtenlandsoppholdHendelse): RegistrertUtenlandsopphold =
    RegistrertUtenlandsopphold.create(
        periode = this.periode,
        dokumentasjon = this.dokumentasjon,
        journalposter = this.journalposter,
        opprettetAv = this.opprettetAv,
        begrunnelse = this.begrunnelse,
        opprettetTidspunkt = this.opprettetTidspunkt,
        endretAv = hendelse.utførtAv,
        endretTidspunkt = hendelse.hendelsestidspunkt,
        versjon = hendelse.versjon,
        erAnnullert = true,
    )
