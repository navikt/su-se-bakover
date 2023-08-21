package no.nav.su.se.bakover.utenlandsopphold.domain.annuller

import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrertUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.korriger.KorrigerUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdHendelse
import java.time.Clock
import java.util.UUID

/**
 *
 *
 * @param hendelseId unik id som identifiserer denne hendelsen på tvers av hendelser globalt
 * @property entitetId samme som [sakId] - et utenlandsopphold er knyttet til en sak og andre utenlandsopphold på den saken.
 */
data class AnnullerUtenlandsoppholdHendelse private constructor(
    override val hendelseId: HendelseId,
    override val tidligereHendelseId: HendelseId,
    override val sakId: UUID,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val meta: HendelseMetadata,
) : UtenlandsoppholdHendelse {

    override val triggetAv: HendelseId? = null

    companion object {

        fun fraPersistert(
            hendelseId: HendelseId,
            tidligereHendelseId: HendelseId,
            sakId: UUID,
            utførtAv: NavIdentBruker.Saksbehandler,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
            meta: HendelseMetadata,
            entitetId: UUID,
        ): AnnullerUtenlandsoppholdHendelse {
            return AnnullerUtenlandsoppholdHendelse(
                hendelseId = hendelseId,
                tidligereHendelseId = tidligereHendelseId,
                sakId = sakId,
                utførtAv = utførtAv,
                hendelsestidspunkt = hendelsestidspunkt,
                versjon = versjon,
                meta = meta,
            ).also {
                require(it.entitetId == entitetId) {
                    "Den persistert entitetId var ulik den utleda fra domenet:${it.entitetId} vs. $entitetId. "
                }
            }
        }

        fun create(
            annullererHendelse: UtenlandsoppholdHendelse,
            hendelseId: HendelseId = HendelseId.generer(),
            utførtAv: NavIdentBruker.Saksbehandler,
            clock: Clock,
            hendelsestidspunkt: Tidspunkt = Tidspunkt.now(clock),
            hendelseMetadata: HendelseMetadata,
            nesteVersjon: Hendelsesversjon,
        ): AnnullerUtenlandsoppholdHendelse {
            require(annullererHendelse is RegistrerUtenlandsoppholdHendelse || annullererHendelse is KorrigerUtenlandsoppholdHendelse) {
                "Kan kun annullere en registrer/korriger-hendelse, men forrige hendelse var: ${annullererHendelse.javaClass.simpleName}"
            }
            return AnnullerUtenlandsoppholdHendelse(
                hendelseId = hendelseId,
                tidligereHendelseId = annullererHendelse.hendelseId,
                sakId = annullererHendelse.sakId,
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
            action = AuditLogEvent.Action.UPDATE,
            // Et utenlandsopphold er ikke knyttet til en behandling, men en sak.
            behandlingId = null,
            callId = this.meta.correlationId?.toString(),
        )
    }
}

fun RegistrertUtenlandsopphold.apply(hendelse: AnnullerUtenlandsoppholdHendelse): RegistrertUtenlandsopphold =
    RegistrertUtenlandsopphold.create(
        periode = this.periode,
        dokumentasjon = this.dokumentasjon,
        journalposter = this.journalposter,
        opprettetAv = this.opprettetAv,
        opprettetTidspunkt = this.opprettetTidspunkt,
        begrunnelse = this.begrunnelse,
        endretAv = hendelse.utførtAv,
        endretTidspunkt = hendelse.hendelsestidspunkt,
        versjon = hendelse.versjon,
        erAnnullert = true,
    )
