package no.nav.su.se.bakover.database.avkorting

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes(
    JsonSubTypes.Type(AvkortingsvarselDb.Opprettet::class),
    JsonSubTypes.Type(AvkortingsvarselDb.SkalAvkortes::class),
    JsonSubTypes.Type(AvkortingsvarselDb.Avkortet::class),
    JsonSubTypes.Type(AvkortingsvarselDb.Annullert::class),
)
sealed class AvkortingsvarselDb {

    abstract val id: UUID
    abstract val sakId: UUID
    abstract val revurderingId: UUID
    abstract val opprettet: Tidspunkt
    abstract val simulering: Simulering

    @JsonTypeName("OPPRETTET")
    data class Opprettet(
        override val id: UUID,
        override val sakId: UUID,
        override val revurderingId: UUID,
        override val opprettet: Tidspunkt,
        override val simulering: Simulering,
    ) : AvkortingsvarselDb()

    @JsonTypeName("SKAL_AVKORTES")
    data class SkalAvkortes(
        override val id: UUID,
        override val sakId: UUID,
        override val revurderingId: UUID,
        override val opprettet: Tidspunkt,
        override val simulering: Simulering,
    ) : AvkortingsvarselDb()

    @JsonTypeName("AVKORTET")
    data class Avkortet(
        override val id: UUID,
        override val sakId: UUID,
        override val revurderingId: UUID,
        override val opprettet: Tidspunkt,
        override val simulering: Simulering,
        val behandlingId: UUID,
    ) : AvkortingsvarselDb()

    @JsonTypeName("ANNULLERT")
    data class Annullert(
        override val id: UUID,
        override val sakId: UUID,
        override val revurderingId: UUID,
        override val opprettet: Tidspunkt,
        override val simulering: Simulering,
        val behandlingId: UUID,
    ) : AvkortingsvarselDb()
}

internal fun Avkortingsvarsel.Utenlandsopphold.toDb(): AvkortingsvarselDb {
    return when (this) {
        is Avkortingsvarsel.Utenlandsopphold.Annullert -> {
            AvkortingsvarselDb.Annullert(
                id = id,
                sakId = sakId,
                revurderingId = revurderingId,
                opprettet = opprettet,
                simulering = simulering,
                behandlingId = behandlingId,
            )
        }
        is Avkortingsvarsel.Utenlandsopphold.Avkortet -> {
            AvkortingsvarselDb.Avkortet(
                id = id,
                sakId = sakId,
                revurderingId = revurderingId,
                opprettet = opprettet,
                simulering = simulering,
                behandlingId = behandlingId,
            )
        }
        is Avkortingsvarsel.Utenlandsopphold.Opprettet -> {
            AvkortingsvarselDb.Opprettet(
                id = id,
                sakId = sakId,
                revurderingId = revurderingId,
                opprettet = opprettet,
                simulering = simulering,
            )
        }
        is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes -> {
            AvkortingsvarselDb.SkalAvkortes(
                id = id,
                sakId = sakId,
                revurderingId = revurderingId,
                opprettet = opprettet,
                simulering = simulering,
            )
        }
    }
}

internal fun AvkortingsvarselDb.toDomain(): Avkortingsvarsel {
    return when (this) {
        is AvkortingsvarselDb.Annullert -> {
            Avkortingsvarsel.Utenlandsopphold.Opprettet(
                id = id,
                sakId = sakId,
                revurderingId = revurderingId,
                opprettet = opprettet,
                simulering = simulering,
            ).skalAvkortes().annuller(behandlingId)
        }
        is AvkortingsvarselDb.Avkortet -> {
            Avkortingsvarsel.Utenlandsopphold.Opprettet(
                id = id,
                sakId = sakId,
                revurderingId = revurderingId,
                opprettet = opprettet,
                simulering = simulering,
            ).skalAvkortes().avkortet(behandlingId)
        }
        is AvkortingsvarselDb.Opprettet -> {
            Avkortingsvarsel.Utenlandsopphold.Opprettet(
                id = id,
                sakId = sakId,
                revurderingId = revurderingId,
                opprettet = opprettet,
                simulering = simulering,
            )
        }
        is AvkortingsvarselDb.SkalAvkortes -> {
            Avkortingsvarsel.Utenlandsopphold.Opprettet(
                id = id,
                sakId = sakId,
                revurderingId = revurderingId,
                opprettet = opprettet,
                simulering = simulering,
            ).skalAvkortes()
        }
    }
}
