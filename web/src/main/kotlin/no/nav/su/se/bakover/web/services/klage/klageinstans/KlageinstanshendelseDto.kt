package no.nav.su.se.bakover.web.services.klage.klageinstans

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.extensions.zoneIdOslo
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.toTidspunkt
import no.nav.su.se.bakover.domain.klage.KlageId
import no.nav.su.se.bakover.domain.klage.KlageinstansUtfall
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
        value = KlageinstanshendelseDto.KlagebehandlingAvsluttetDetaljer::class,
        name = "KLAGEBEHANDLING_AVSLUTTET",
    ),
    JsonSubTypes.Type(
        value = KlageinstanshendelseDto.AnkebehandlingOpprettetDetaljer::class,
        name = "ANKEBEHANDLING_OPPRETTET",
    ),
    JsonSubTypes.Type(
        value = KlageinstanshendelseDto.AnkebehandlingAvsluttetDetaljer::class,
        name = "ANKEBEHANDLING_AVSLUTTET",
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

    data class KlagebehandlingAvsluttetDetaljer(
        override val kildeReferanse: String,
        val detaljer: DetaljerWrapper,
    ) : KlageinstanshendelseDto {
        override fun toDomain(
            id: UUID,
            opprettet: Tidspunkt,
        ): Either<KunneIkkeTolkeKlageinstanshendelse.UgyldigeVerdier, TolketKlageinstanshendelse> {
            return Either.catch {
                TolketKlageinstanshendelse(
                    id = id,
                    opprettet = opprettet,
                    avsluttetTidspunkt = parseKabalDatetime(detaljer.klagebehandlingAvsluttet.avsluttet),
                    klageId = KlageId(UUID.fromString(kildeReferanse)),
                    utfall = detaljer.klagebehandlingAvsluttet.utfall.toDomain(),
                    journalpostIDer = detaljer.klagebehandlingAvsluttet.journalpostReferanser.map { JournalpostId(it) },
                )
            }.mapLeft {
                log.error("Kunne ikke tolke klageinstanshendelse.", it)
                KunneIkkeTolkeKlageinstanshendelse.UgyldigeVerdier
            }
        }

        data class DetaljerWrapper(
            val klagebehandlingAvsluttet: Detaljer,
        )

        data class Detaljer(
            val avsluttet: String,
            val utfall: Utfall,
            val journalpostReferanser: List<String>,
        )
    }

    data class AnkebehandlingOpprettetDetaljer(
        override val kildeReferanse: String,
    ) : KlageinstanshendelseDto {
        override fun toDomain(id: UUID, opprettet: Tidspunkt) =
            KunneIkkeTolkeKlageinstanshendelse.AnkehendelserStøttesIkke.left()
    }

    data class AnkebehandlingAvsluttetDetaljer(
        override val kildeReferanse: String,
    ) : KlageinstanshendelseDto {
        override fun toDomain(id: UUID, opprettet: Tidspunkt) =
            KunneIkkeTolkeKlageinstanshendelse.AnkehendelserStøttesIkke.left()
    }

    enum class Utfall {
        TRUKKET,
        RETUR,
        OPPHEVET,
        MEDHOLD,
        DELVIS_MEDHOLD,
        STADFESTELSE,
        UGUNST,
        AVVIST,
        ;

        fun toDomain(): KlageinstansUtfall = when (this) {
            TRUKKET -> KlageinstansUtfall.TRUKKET
            RETUR -> KlageinstansUtfall.RETUR
            OPPHEVET -> KlageinstansUtfall.OPPHEVET
            MEDHOLD -> KlageinstansUtfall.MEDHOLD
            DELVIS_MEDHOLD -> KlageinstansUtfall.DELVIS_MEDHOLD
            STADFESTELSE -> KlageinstansUtfall.STADFESTELSE
            UGUNST -> KlageinstansUtfall.UGUNST
            AVVIST -> KlageinstansUtfall.AVVIST
        }
    }
}

private fun parseKabalDatetime(isoString: String): Tidspunkt {
    return try {
        // Dersom Kabal begynner å legge på tidssone, skal vi kunne parse den direkte til en [Instant].
        Instant.parse(isoString).toTidspunkt()
    } catch (e: DateTimeParseException) {
        // Kabal sender i skrivende stund en ISOstreng uten tidssoneinformasjon (LocalDateTime). Deres default tidssone er i skrivende stund CET.
        LocalDateTime.parse(isoString).toTidspunkt(zoneIdOslo)
    }
}
