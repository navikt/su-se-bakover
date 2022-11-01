package no.nav.su.se.bakover.utenlandsopphold.domain.korriger

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.periode.DatoIntervall
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrertUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdDokumentasjon
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdHendelse
import java.time.Clock
import java.util.UUID

/**
 * @param hendelseId unik id som identifiserer denne hendelsen på tvers av hendelser globalt
 * @property entitetId samme som [sakId] - et utenlandsopphold er knyttet til en sak og andre utenlandsopphold på den saken.
 */
data class KorrigerUtenlandsoppholdHendelse private constructor(
    override val hendelseId: HendelseId,
    override val tidligereHendelseId: HendelseId,
    override val sakId: UUID,
    val periode: DatoIntervall,
    val dokumentasjon: UtenlandsoppholdDokumentasjon,
    val journalposter: List<JournalpostId>,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val meta: HendelseMetadata,
) : UtenlandsoppholdHendelse {

    companion object {

        fun fraPersistert(
            hendelseId: HendelseId,
            tidligereHendelseId: HendelseId,
            sakId: UUID,
            periode: DatoIntervall,
            dokumentasjon: UtenlandsoppholdDokumentasjon,
            journalposter: List<JournalpostId>,
            utførtAv: NavIdentBruker.Saksbehandler,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
            hendelseMetadata: HendelseMetadata,
            entitetId: UUID,
        ): KorrigerUtenlandsoppholdHendelse {
            return KorrigerUtenlandsoppholdHendelse(
                hendelseId = hendelseId,
                tidligereHendelseId = tidligereHendelseId,
                sakId = sakId,
                periode = periode,
                dokumentasjon = dokumentasjon,
                journalposter = journalposter,
                utførtAv = utførtAv,
                hendelsestidspunkt = hendelsestidspunkt,
                versjon = versjon,
                meta = hendelseMetadata,
            ).also {
                require(it.entitetId == entitetId) {
                    "Den persistert entitetId var ulik den utleda fra domenet:${it.entitetId} vs. $entitetId. "
                }
            }
        }
        fun create(
            korrigererHendelse: UtenlandsoppholdHendelse,
            hendelseId: HendelseId = HendelseId.generer(),
            periode: DatoIntervall,
            dokumentasjon: UtenlandsoppholdDokumentasjon,
            journalposter: List<JournalpostId>,
            utførtAv: NavIdentBruker.Saksbehandler,
            clock: Clock,
            hendelsestidspunkt: Tidspunkt = Tidspunkt.now(clock),
            hendelseMetadata: HendelseMetadata,
            nesteVersjon: Hendelsesversjon,
        ): KorrigerUtenlandsoppholdHendelse {
            require(korrigererHendelse is RegistrerUtenlandsoppholdHendelse || korrigererHendelse is KorrigerUtenlandsoppholdHendelse) {
                "Kan kun annullere en registrer/korrigere-hendelse, men forrige hendelse var: ${korrigererHendelse.javaClass.simpleName}"
            }
            return KorrigerUtenlandsoppholdHendelse(
                hendelseId = hendelseId,
                tidligereHendelseId = korrigererHendelse.hendelseId,
                sakId = korrigererHendelse.sakId,
                periode = periode,
                dokumentasjon = dokumentasjon,
                journalposter = journalposter,
                utførtAv = utførtAv,
                hendelsestidspunkt = hendelsestidspunkt,
                versjon = nesteVersjon,
                meta = hendelseMetadata,
            )
        }
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
}

fun RegistrertUtenlandsopphold.apply(hendelse: KorrigerUtenlandsoppholdHendelse): RegistrertUtenlandsopphold = RegistrertUtenlandsopphold.create(
    periode = hendelse.periode,
    dokumentasjon = hendelse.dokumentasjon,
    journalposter = hendelse.journalposter,
    opprettetAv = this.opprettetAv,
    opprettetTidspunkt = this.opprettetTidspunkt,
    endretAv = hendelse.utførtAv,
    endretTidspunkt = hendelse.hendelsestidspunkt,
    versjon = hendelse.versjon,
    erAnnullert = false,
)
