package no.nav.su.se.bakover.institusjonsopphold.database

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdKildeDb.Companion.toJson
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdTypeDb.Companion.toJson

data class InstitusjonsoppholdHendelseData(
    /**
     * Referer til det eksterne hendelsesId'en - Se [EksternInstitusjonsoppholdHendelse]
     */
    val hendelseId: Long,
    val oppholdId: Long,
    val norskident: String,
    val type: InstitusjonsoppholdTypeDb,
    val kilde: InstitusjonsoppholdKildeDb,
    val oppgaveId: String?,
) {
    companion object {
        fun InstitusjonsoppholdHendelse.toJson(): InstitusjonsoppholdHendelseData = when (this) {
            is InstitusjonsoppholdHendelse.UtenOppgaveId -> InstitusjonsoppholdHendelseData(
                hendelseId = this.eksterneHendelse.hendelseId,
                oppholdId = this.eksterneHendelse.oppholdId,
                norskident = this.eksterneHendelse.norskident.toString(),
                type = this.eksterneHendelse.type.toJson(),
                kilde = this.eksterneHendelse.kilde.toJson(),
                oppgaveId = null,
            )

            is InstitusjonsoppholdHendelse.MedOppgaveId -> InstitusjonsoppholdHendelseData(
                hendelseId = this.eksterneHendelse.hendelseId,
                oppholdId = this.eksterneHendelse.oppholdId,
                norskident = this.eksterneHendelse.norskident.toString(),
                type = this.eksterneHendelse.type.toJson(),
                kilde = this.eksterneHendelse.kilde.toJson(),
                oppgaveId = this.oppgaveId.toString(),
            )
        }

        fun InstitusjonsoppholdHendelse.toStringifiedJson(): String = serialize(this.toJson())
    }
}
