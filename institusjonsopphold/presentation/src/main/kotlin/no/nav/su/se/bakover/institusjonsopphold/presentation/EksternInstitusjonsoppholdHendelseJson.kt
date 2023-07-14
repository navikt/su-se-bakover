package no.nav.su.se.bakover.institusjonsopphold.presentation

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.EksternInstitusjonsoppholdHendelse

/**
 * https://github.com/navikt/institusjon/blob/main/apps/institusjon-opphold-hendelser/src/main/java/no/nav/opphold/hendelser/producer/domain/KafkaOppholdHendelse.java
 */
data class EksternInstitusjonsoppholdHendelseJson(
    val hendelseId: Long,
    val oppholdId: Long,
    val norskident: String,
    val type: EksternInstitusjonsoppholdTypeJson,
    val kilde: EksternInstitusjonsoppholdKildeJson,
) {
    fun toDomain(): EksternInstitusjonsoppholdHendelse =
        EksternInstitusjonsoppholdHendelse(
            hendelseId = hendelseId,
            oppholdId = oppholdId,
            norskident = Fnr.tryCreate(norskident)
                ?: throw IllegalArgumentException("Kunne ikke lage fødselsnummer av ident for hendelse $hendelseId, opphold $oppholdId. Er dette ikke et fnr?"),
            type = type.toDomain(),
            kilde = kilde.toDomain(),
        )
}
