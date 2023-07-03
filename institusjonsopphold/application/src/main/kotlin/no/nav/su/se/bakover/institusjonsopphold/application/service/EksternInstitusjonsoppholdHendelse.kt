package no.nav.su.se.bakover.institusjonsopphold.application.service

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse

/**
 * https://github.com/navikt/institusjon/blob/main/apps/institusjon-opphold-hendelser/src/main/java/no/nav/opphold/hendelser/producer/domain/KafkaOppholdHendelse.java
 */
data class EksternInstitusjonsoppholdHendelse(
    val hendelseId: Long,
    val oppholdId: Long,
    val norskident: String,
    val type: EksternInstitusjonsoppholdType,
    val kilde: EksternInstitusjonsoppholdKilde,
) {
    fun toDomain(): InstitusjonsoppholdHendelse = InstitusjonsoppholdHendelse(
        hendelseId = hendelseId,
        oppholdId = oppholdId,
        norskident = Fnr.tryCreate(norskident)
            ?: throw IllegalArgumentException("Kunne ikke lage fødselsnummer av ident for hendelse $hendelseId, opphold $oppholdId. Er dette ikke et fnr?"),
        type = type.toDomain(),
        kilde = kilde.toDomain(),
    )
}
