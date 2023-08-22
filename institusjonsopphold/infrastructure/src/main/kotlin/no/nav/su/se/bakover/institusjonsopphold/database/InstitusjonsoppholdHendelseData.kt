package no.nav.su.se.bakover.institusjonsopphold.database

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.EksternInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdKildeDb.Companion.toJson
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdTypeDb.Companion.toJson

data class InstitusjonsoppholdHendelseData(
    /**
     * Referer til det eksterne hendelsesId'en, og ikke vår hendelsesId som er definert i [Hendelse] - Se [EksternInstitusjonsoppholdHendelse]
     */
    val hendelseId: Long,
    val oppholdId: Long,
    val norskident: String,
    val type: InstitusjonsoppholdTypeDb,
    val kilde: InstitusjonsoppholdKildeDb,
) {
    companion object {
        fun InstitusjonsoppholdHendelse.toJson(): InstitusjonsoppholdHendelseData = InstitusjonsoppholdHendelseData(
            hendelseId = this.eksterneHendelse.hendelseId,
            oppholdId = this.eksterneHendelse.oppholdId.value,
            norskident = this.eksterneHendelse.norskident.toString(),
            type = this.eksterneHendelse.type.toJson(),
            kilde = this.eksterneHendelse.kilde.toJson(),
        )

        fun InstitusjonsoppholdHendelse.toStringifiedJson(): String = serialize(this.toJson())
    }
}
