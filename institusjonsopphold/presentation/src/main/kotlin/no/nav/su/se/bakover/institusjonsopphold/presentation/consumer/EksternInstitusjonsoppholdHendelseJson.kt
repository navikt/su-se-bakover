package no.nav.su.se.bakover.institusjonsopphold.presentation.consumer

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import java.time.Clock
import java.util.UUID

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
    fun toDomain(clock: Clock): InstitusjonsoppholdHendelse.IkkeKnyttetTilSak =
        InstitusjonsoppholdHendelse.IkkeKnyttetTilSak(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            eksternHendelse = no.nav.su.se.bakover.domain.EksternInstitusjonsoppholdHendelse(
                hendelseId = hendelseId,
                oppholdId = oppholdId,
                norskident = Fnr.tryCreate(norskident)
                    ?: throw IllegalArgumentException("Kunne ikke lage fødselsnummer av ident for hendelse $hendelseId, opphold $oppholdId. Er dette ikke et fnr?"),
                type = type.toDomain(),
                kilde = kilde.toDomain(),
            ),
        )
}
