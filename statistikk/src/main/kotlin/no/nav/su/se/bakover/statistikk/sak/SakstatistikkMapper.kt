package no.nav.su.se.bakover.statistikk.sak

import arrow.core.Either
import com.networknt.schema.JsonSchema
import com.networknt.schema.ValidationMessage
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.statistikk.SchemaValidator
import no.nav.su.se.bakover.statistikk.ValidertStatistikkJsonMelding

private val sakSchema: JsonSchema = SchemaValidator.createSchema("/statistikk/sak_schema.json")

internal fun StatistikkEvent.SakOpprettet.toBehandlingsstatistikk(
    aktørId: AktørId,
    gitCommit: GitCommit?,
): Either<Set<ValidationMessage>, ValidertStatistikkJsonMelding> {
    return SaksstatistikkDto(
        funksjonellTid = sak.opprettet,
        tekniskTid = sak.opprettet,
        opprettetDato = sak.opprettet.toLocalDate(zoneIdOslo),
        sakId = sak.id,
        aktorId = aktørId.toString().toLong(),
        saksnummer = sak.saksnummer.nummer,
        sakStatus = "OPPRETTET",
        sakStatusBeskrivelse = "Sak er opprettet men ingen vedtak er fattet.",
        versjon = gitCommit?.value,
    ).let {
        serialize(it).let {
            SchemaValidator.validate(it, sakSchema).map {
                ValidertStatistikkJsonMelding(
                    topic = "supstonad.aapen-su-sak-statistikk-v1",
                    validertJsonMelding = it,
                )
            }
        }
    }
}
