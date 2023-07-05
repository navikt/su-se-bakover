package no.nav.su.se.bakover.institusjonsopphold.database

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdKildeDb.Companion.toDb
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdTypeDb.Companion.toDb
import java.util.UUID

data class InstitusjonsoppholdHendelseDb(
    val id: UUID,
    val opprettet: Tidspunkt,
    val sakId: UUID,
    val hendelseId: Long,
    val oppholdId: Long,
    val norskident: String,
    val type: InstitusjonsoppholdTypeDb,
    val kilde: InstitusjonsoppholdKildeDb,
    val oppgaveId: String?,
) {
    companion object {
        fun InstitusjonsoppholdHendelse.toDb(): InstitusjonsoppholdHendelseDb = when (this) {
            is InstitusjonsoppholdHendelse.IkkeKnyttetTilSak -> throw IllegalArgumentException("Kan ikke lagre hendelse. ekstern hendelse id ${this.eksternHendelse.hendelseId}")
            is InstitusjonsoppholdHendelse.KnyttetTilSak -> InstitusjonsoppholdHendelseDb(
                id = this.id,
                opprettet = this.opprettet,
                sakId = this.sakId,
                hendelseId = this.eksternHendelse.hendelseId,
                oppholdId = this.eksternHendelse.oppholdId,
                norskident = this.eksternHendelse.norskident.toString(),
                type = this.eksternHendelse.type.toDb(),
                kilde = this.eksternHendelse.kilde.toDb(),
                oppgaveId = this.oppgaveId?.toString(),
            )
        }
    }
}
