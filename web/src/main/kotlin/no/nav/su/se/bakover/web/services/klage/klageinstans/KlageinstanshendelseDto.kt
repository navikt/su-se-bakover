package no.nav.su.se.bakover.web.services.klage.klageinstans

import arrow.core.Either
import arrow.core.flatMap
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.toTidspunkt
import no.nav.su.se.bakover.domain.klage.KunneIkkeTolkeKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * https://github.com/navikt/kabal-api/blob/main/src/main/kotlin/no/nav/klage/oppgave/domain/kafka/BehandlingEvent.kt
 * https://github.com/navikt/kabal-api/blob/main/docs/schema/behandling-events.json
 *
 * Vi deserialiserer ikke feltet `kilde` på dette tidspunktet, da vi filtrer bort de som ikke tilhører `su-se-bakover` tidligere.
 * Vi deserialiserer feltene `type` og `detaljer` inn i et sealed interface vha. @JsonSubTypes og @JsonTypeInfo
 * Feltet `eventId` brukes kun til dedup ved lagring og trengs ikke her.
 * Feltet `kabalReferanse` brukes ikke av oss til noe, så det ignoreres.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(
        value = KlagebehandlingAvsluttetDto::class,
        name = "KLAGEBEHANDLING_AVSLUTTET",
    ),
    JsonSubTypes.Type(
        value = AnkebehandlingOpprettetDto::class,
        name = "ANKEBEHANDLING_OPPRETTET",
    ),
    JsonSubTypes.Type(
        value = AnkebehandlingAvsluttetDto::class,
        name = "ANKEBEHANDLING_AVSLUTTET",
    ),
    JsonSubTypes.Type(
        value = AnkeITrygderettenbehandlingOpprettetDto::class,
        name = "ANKE_I_TRYGDERETTENBEHANDLING_OPPRETTET",
    ),
    JsonSubTypes.Type(
        value = BehandlingEtterTrygderettenOpphevetAvsluttet::class,
        name = "BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET_AVSLUTTET",
    ),
    JsonSubTypes.Type(
        value = BehandlingFeilregistrertDto::class,
        name = "BEHANDLING_FEILREGISTRERT",
    ),
)
sealed interface KlageinstanshendelseDto {

    /**
     * Ekstern id for klage.
     * Skal stemme overens med id sendt inn.
     */
    val kildeReferanse: String

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
        fun toDomain(
            id: UUID,
            opprettet: Tidspunkt,
            json: String,
        ): Either<KunneIkkeTolkeKlageinstanshendelse, TolketKlageinstanshendelse> {
            return Either.catch {
                deserialize<KlageinstanshendelseDto>(json)
            }.mapLeft {
                log.error("Kunne ikke deserialisere klageinstanshendelse", it)
                KunneIkkeTolkeKlageinstanshendelse.KunneIkkeDeserialisere
            }.flatMap { it.toDomain(id, opprettet) }
        }
    }

    fun toDomain(
        id: UUID,
        opprettet: Tidspunkt,
    ): Either<KunneIkkeTolkeKlageinstanshendelse, TolketKlageinstanshendelse>
}

internal fun parseKabalDatetime(isoString: String): Tidspunkt {
    return try {
        // Dersom Kabal begynner å legge på tidssone, skal vi kunne parse den direkte til en [Instant].
        Instant.parse(isoString).toTidspunkt()
    } catch (e: DateTimeParseException) {
        // Kabal sender i skrivende stund en ISOstreng uten tidssoneinformasjon (LocalDateTime). Deres default tidssone er i skrivende stund CET.
        LocalDateTime.parse(isoString).toTidspunkt(zoneIdOslo)
    }
}
