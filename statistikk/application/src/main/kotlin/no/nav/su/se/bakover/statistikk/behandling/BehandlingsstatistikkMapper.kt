package no.nav.su.se.bakover.statistikk.behandling

import arrow.core.Either
import com.networknt.schema.JsonSchema
import com.networknt.schema.ValidationMessage
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.statistikk.SchemaValidator
import no.nav.su.se.bakover.statistikk.SchemaValidator.createSchema
import no.nav.su.se.bakover.statistikk.ValidertStatistikkJsonMelding
import no.nav.su.se.bakover.statistikk.behandling.klage.toBehandlingsstatistikkDto
import no.nav.su.se.bakover.statistikk.behandling.revurdering.gjenopptak.toBehandlingsstatistikkDto
import no.nav.su.se.bakover.statistikk.behandling.revurdering.revurdering.toBehandlingsstatistikkDto
import no.nav.su.se.bakover.statistikk.behandling.revurdering.stans.toBehandlingsstatistikkDto
import no.nav.su.se.bakover.statistikk.behandling.søknad.toBehandlingsstatistikkDto
import no.nav.su.se.bakover.statistikk.behandling.søknadsbehandling.toBehandlingsstatistikkDto
import java.time.Clock

private val behandlingSchema: JsonSchema = createSchema("/statistikk/behandling_schema.json")

internal fun StatistikkEvent.Søknad.toBehandlingsstatistikk(
    gitCommit: GitCommit?,
    clock: Clock,
): Either<Set<ValidationMessage>, ValidertStatistikkJsonMelding> {
    return serializeAndValidate(this.toBehandlingsstatistikkDto(gitCommit, clock))
}

internal fun StatistikkEvent.Behandling.toBehandlingsstatistikkDto(
    gitCommit: GitCommit?,
    clock: Clock,
): Either<Set<ValidationMessage>, ValidertStatistikkJsonMelding> {
    return when (this) {
        is StatistikkEvent.Behandling.Søknad -> this.toBehandlingsstatistikkDto(gitCommit, clock)
        is StatistikkEvent.Behandling.Klage -> this.toBehandlingsstatistikkDto(gitCommit, clock)
        is StatistikkEvent.Behandling.Revurdering -> this.toBehandlingsstatistikkDto(gitCommit, clock)
        is StatistikkEvent.Behandling.Stans -> this.toBehandlingsstatistikkDto(gitCommit, clock)
        is StatistikkEvent.Behandling.Gjenoppta -> this.toBehandlingsstatistikkDto(gitCommit, clock)
        is StatistikkEvent.Behandling.AvslåttOmgjøring.Omgjøring -> this.toBehandlingsstatistikkDto(gitCommit, clock)
    }.let {
        serializeAndValidate(it)
    }
}

private fun serializeAndValidate(it: BehandlingsstatistikkDto): Either<Set<ValidationMessage>, ValidertStatistikkJsonMelding> {
    return serialize(it).let {
        SchemaValidator.validate(it, behandlingSchema).map {
            ValidertStatistikkJsonMelding(
                topic = "supstonad.aapen-su-behandling-statistikk-v1",
                validertJsonMelding = it,
            )
        }
    }
}
