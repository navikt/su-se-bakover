package no.nav.su.se.bakover.database.avkorting

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.database.simulering.SimuleringDatabaseJson
import no.nav.su.se.bakover.database.simulering.SimuleringDatabaseJson.Companion.toDatabaseJson
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes(
    JsonSubTypes.Type(AvkortingsvarselDb.Opprettet::class),
    JsonSubTypes.Type(AvkortingsvarselDb.SkalAvkortes::class),
    JsonSubTypes.Type(AvkortingsvarselDb.Avkortet::class),
    JsonSubTypes.Type(AvkortingsvarselDb.Annullert::class),
)
internal sealed class AvkortingsvarselDb {

    abstract val id: UUID
    abstract val sakId: UUID
    abstract val revurderingId: UUID
    abstract val opprettet: Tidspunkt
    abstract val simulering: SimuleringDatabaseJson

    @JsonTypeName("OPPRETTET")
    data class Opprettet(
        override val id: UUID,
        override val sakId: UUID,
        override val revurderingId: UUID,
        override val opprettet: Tidspunkt,
        override val simulering: SimuleringDatabaseJson,
    ) : AvkortingsvarselDb()

    @JsonTypeName("SKAL_AVKORTES")
    data class SkalAvkortes(
        override val id: UUID,
        override val sakId: UUID,
        override val revurderingId: UUID,
        override val opprettet: Tidspunkt,
        override val simulering: SimuleringDatabaseJson,
    ) : AvkortingsvarselDb()

    @JsonTypeName("AVKORTET")
    data class Avkortet(
        override val id: UUID,
        override val sakId: UUID,
        override val revurderingId: UUID,
        override val opprettet: Tidspunkt,
        override val simulering: SimuleringDatabaseJson,
        val behandlingId: UUID,
    ) : AvkortingsvarselDb()

    @JsonTypeName("ANNULLERT")
    data class Annullert(
        override val id: UUID,
        override val sakId: UUID,
        override val revurderingId: UUID,
        override val opprettet: Tidspunkt,
        override val simulering: SimuleringDatabaseJson,
        val behandlingId: UUID,
    ) : AvkortingsvarselDb()
}

internal fun Avkortingsvarsel.Utenlandsopphold.toDb(): AvkortingsvarselDb {
    return when (this) {
        is Avkortingsvarsel.Utenlandsopphold.Annullert -> {
            toDb()
        }
        is Avkortingsvarsel.Utenlandsopphold.Avkortet -> {
            toDb()
        }
        is Avkortingsvarsel.Utenlandsopphold.Opprettet -> {
            toDb()
        }
        is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes -> {
            toDb()
        }
    }
}

internal fun Avkortingsvarsel.Utenlandsopphold.SkalAvkortes.toDb(): AvkortingsvarselDb.SkalAvkortes {
    return AvkortingsvarselDb.SkalAvkortes(
        id = id,
        sakId = sakId,
        revurderingId = revurderingId,
        opprettet = opprettet,
        simulering = simulering.toDatabaseJson(),
    )
}

internal fun Avkortingsvarsel.Utenlandsopphold.Avkortet.toDb(): AvkortingsvarselDb.Avkortet {
    return AvkortingsvarselDb.Avkortet(
        id = id,
        sakId = sakId,
        revurderingId = revurderingId,
        opprettet = opprettet,
        simulering = simulering.toDatabaseJson(),
        behandlingId = behandlingId,
    )
}

internal fun Avkortingsvarsel.Utenlandsopphold.Opprettet.toDb(): AvkortingsvarselDb.Opprettet {
    return AvkortingsvarselDb.Opprettet(
        id = id,
        sakId = sakId,
        revurderingId = revurderingId,
        opprettet = opprettet,
        simulering = simulering.toDatabaseJson(),
    )
}

internal fun Avkortingsvarsel.Utenlandsopphold.Annullert.toDb(): AvkortingsvarselDb.Annullert {
    return AvkortingsvarselDb.Annullert(
        id = id,
        sakId = sakId,
        revurderingId = revurderingId,
        opprettet = opprettet,
        simulering = simulering.toDatabaseJson(),
        behandlingId = behandlingId,
    )
}

internal fun AvkortingsvarselDb.toDomain(): Avkortingsvarsel {
    return when (this) {
        is AvkortingsvarselDb.Annullert -> {
            toDomain()
        }
        is AvkortingsvarselDb.Avkortet -> {
            toDomain()
        }
        is AvkortingsvarselDb.Opprettet -> {
            toDomain()
        }
        is AvkortingsvarselDb.SkalAvkortes -> {
            toDomain()
        }
    }
}

internal fun AvkortingsvarselDb.SkalAvkortes.toDomain(): Avkortingsvarsel.Utenlandsopphold.SkalAvkortes {
    return Avkortingsvarsel.Utenlandsopphold.Opprettet(
        id = id,
        sakId = sakId,
        revurderingId = revurderingId,
        opprettet = opprettet,
        simulering = simulering.toDomain(),
    ).skalAvkortes()
}

internal fun AvkortingsvarselDb.Opprettet.toDomain(): Avkortingsvarsel.Utenlandsopphold.Opprettet {
    return Avkortingsvarsel.Utenlandsopphold.Opprettet(
        id = id,
        sakId = sakId,
        revurderingId = revurderingId,
        opprettet = opprettet,
        simulering = simulering.toDomain(),
    )
}

internal fun AvkortingsvarselDb.Annullert.toDomain(): Avkortingsvarsel.Utenlandsopphold.Annullert {
    return Avkortingsvarsel.Utenlandsopphold.Opprettet(
        id = id,
        sakId = sakId,
        revurderingId = revurderingId,
        opprettet = opprettet,
        simulering = simulering.toDomain(),
    ).skalAvkortes().annuller(behandlingId)
}

internal fun AvkortingsvarselDb.Avkortet.toDomain(): Avkortingsvarsel.Utenlandsopphold.Avkortet {
    return Avkortingsvarsel.Utenlandsopphold.Opprettet(
        id = id,
        sakId = sakId,
        revurderingId = revurderingId,
        opprettet = opprettet,
        simulering = simulering.toDomain(),
    ).skalAvkortes().avkortet(behandlingId)
}
