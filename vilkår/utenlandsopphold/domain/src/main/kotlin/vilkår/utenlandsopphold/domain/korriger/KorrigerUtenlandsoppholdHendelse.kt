package vilkår.utenlandsopphold.domain.korriger

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import vilkår.utenlandsopphold.domain.RegistrertUtenlandsopphold
import vilkår.utenlandsopphold.domain.UtenlandsoppholdDokumentasjon
import vilkår.utenlandsopphold.domain.UtenlandsoppholdHendelse
import vilkår.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdHendelse
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
    val begrunnelse: String?,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
) : UtenlandsoppholdHendelse {

    companion object {

        fun fraPersistert(
            hendelseId: HendelseId,
            tidligereHendelseId: HendelseId,
            sakId: UUID,
            periode: DatoIntervall,
            dokumentasjon: UtenlandsoppholdDokumentasjon,
            journalposter: List<JournalpostId>,
            begrunnelse: String?,
            utførtAv: NavIdentBruker.Saksbehandler,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
            entitetId: UUID,
        ): KorrigerUtenlandsoppholdHendelse {
            return KorrigerUtenlandsoppholdHendelse(
                hendelseId = hendelseId,
                tidligereHendelseId = tidligereHendelseId,
                sakId = sakId,
                periode = periode,
                dokumentasjon = dokumentasjon,
                journalposter = journalposter,
                begrunnelse = begrunnelse,
                utførtAv = utførtAv,
                hendelsestidspunkt = hendelsestidspunkt,
                versjon = versjon,
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
            begrunnelse: String?,
            utførtAv: NavIdentBruker.Saksbehandler,
            clock: Clock,
            hendelsestidspunkt: Tidspunkt = Tidspunkt.now(clock),
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
                begrunnelse = begrunnelse,
                utførtAv = utførtAv,
                hendelsestidspunkt = hendelsestidspunkt,
                versjon = nesteVersjon,
            )
        }
    }

    fun toAuditEvent(
        berørtBrukerId: Fnr,
        correlationId: CorrelationId,
    ): AuditLogEvent {
        return AuditLogEvent(
            navIdent = this.utførtAv.toString(),
            berørtBrukerId = berørtBrukerId,
            action = AuditLogEvent.Action.CREATE,
            // Et utenlandsopphold er ikke knyttet til en behandling, men en sak.
            behandlingId = null,
            callId = correlationId.toString(),
        )
    }
}

fun RegistrertUtenlandsopphold.apply(hendelse: KorrigerUtenlandsoppholdHendelse): RegistrertUtenlandsopphold = RegistrertUtenlandsopphold.create(
    periode = hendelse.periode,
    dokumentasjon = hendelse.dokumentasjon,
    journalposter = hendelse.journalposter,
    opprettetAv = this.opprettetAv,
    begrunnelse = hendelse.begrunnelse,
    opprettetTidspunkt = this.opprettetTidspunkt,
    endretAv = hendelse.utførtAv,
    endretTidspunkt = hendelse.hendelsestidspunkt,
    versjon = hendelse.versjon,
    erAnnullert = false,
)
