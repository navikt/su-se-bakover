package no.nav.su.se.bakover.database.avkorting

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import no.nav.su.se.bakover.database.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import java.util.UUID

internal fun AvkortingVedSøknadsbehandling.toDb(): AvkortingVedSøknadsbehandlingDb {
    return when (this) {
        is AvkortingVedSøknadsbehandling.Håndtert -> {
            toDb()
        }
        is AvkortingVedSøknadsbehandling.Iverksatt -> {
            toDb()
        }
        is AvkortingVedSøknadsbehandling.Uhåndtert -> {
            toDb()
        }
    }
}

internal fun AvkortingVedSøknadsbehandlingDb.toDomain(
    avkortingsvarselPostgresRepo: AvkortingsvarselPostgresRepo,
    session: Session,
): AvkortingVedSøknadsbehandling {
    return when (this) {
        is AvkortingVedSøknadsbehandlingDb.Håndtert -> {
            toDomain(avkortingsvarselPostgresRepo, session)
        }
        is AvkortingVedSøknadsbehandlingDb.Iverksatt -> {
            toDomain(avkortingsvarselPostgresRepo, session)
        }
        is AvkortingVedSøknadsbehandlingDb.Uhåndtert -> {
            toDomain(avkortingsvarselPostgresRepo, session)
        }
    }
}

internal fun AvkortingVedSøknadsbehandlingDb.Håndtert.toDomain(
    avkortingsvarselPostgresRepo: AvkortingsvarselPostgresRepo,
    session: Session,
): AvkortingVedSøknadsbehandling.Håndtert {
    return when (this) {
        is AvkortingVedSøknadsbehandlingDb.Håndtert.AvkortUtestående -> {
            AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
                AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
                    avkortingsvarsel = avkortingsvarselPostgresRepo.hent(
                        id = avkortingsvarselId,
                        session = session,
                    ) as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
                ),
            )
        }
        is AvkortingVedSøknadsbehandlingDb.Håndtert.IngenUtestående -> {
            AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående
        }
        is AvkortingVedSøknadsbehandlingDb.Håndtert.KanIkkeHåndtere -> {
            AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere
        }
    }
}

internal fun AvkortingVedSøknadsbehandlingDb.Iverksatt.toDomain(
    avkortingsvarselPostgresRepo: AvkortingsvarselPostgresRepo,
    session: Session,
): AvkortingVedSøknadsbehandling.Iverksatt {
    return when (this) {
        is AvkortingVedSøknadsbehandlingDb.Iverksatt.AvkortUtestående -> {
            AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående(
                avkortUtestående = avkortingsvarselPostgresRepo.hent(
                    id = avkortingsvarselId,
                    session = session,
                ) as Avkortingsvarsel.Utenlandsopphold.Avkortet,
            )
        }
        is AvkortingVedSøknadsbehandlingDb.Iverksatt.IngenUtestående -> {
            AvkortingVedSøknadsbehandling.Iverksatt.IngenUtestående
        }
        is AvkortingVedSøknadsbehandlingDb.Iverksatt.KanIkkeHåndtere -> {
            AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere
        }
    }
}

internal fun AvkortingVedSøknadsbehandlingDb.Uhåndtert.toDomain(
    avkortingsvarselPostgresRepo: AvkortingsvarselPostgresRepo,
    session: Session,
): AvkortingVedSøknadsbehandling.Uhåndtert {
    return when (this) {
        is AvkortingVedSøknadsbehandlingDb.Uhåndtert.IngenUtestående -> {
            AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående
        }
        is AvkortingVedSøknadsbehandlingDb.Uhåndtert.UteståendeAvkorting -> {
            AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
                avkortingsvarsel = avkortingsvarselPostgresRepo.hent(
                    id = avkortingsvarselId,
                    session = session,
                ) as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
            )
        }
        is AvkortingVedSøknadsbehandlingDb.Uhåndtert.KanIkkeHåndtere -> {
            AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere
        }
    }
}

internal fun AvkortingVedSøknadsbehandling.Uhåndtert.toDb(): AvkortingVedSøknadsbehandlingDb.Uhåndtert {
    return when (this) {
        is AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående -> {
            AvkortingVedSøknadsbehandlingDb.Uhåndtert.IngenUtestående
        }
        is AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting -> {
            AvkortingVedSøknadsbehandlingDb.Uhåndtert.UteståendeAvkorting(
                avkortingsvarselId = avkortingsvarsel.id,
            )
        }
        is AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere -> {
            AvkortingVedSøknadsbehandlingDb.Uhåndtert.KanIkkeHåndtere
        }
    }
}

internal fun AvkortingVedSøknadsbehandling.Håndtert.toDb(): AvkortingVedSøknadsbehandlingDb.Håndtert {
    return when (this) {
        is AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående -> {
            AvkortingVedSøknadsbehandlingDb.Håndtert.AvkortUtestående(
                avkortingsvarselId = avkortUtestående.avkortingsvarsel.id,
            )
        }
        is AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående -> {
            AvkortingVedSøknadsbehandlingDb.Håndtert.IngenUtestående
        }
        is AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere -> {
            AvkortingVedSøknadsbehandlingDb.Håndtert.KanIkkeHåndtere
        }
    }
}

internal fun AvkortingVedSøknadsbehandling.Iverksatt.toDb(): AvkortingVedSøknadsbehandlingDb.Iverksatt {
    return when (this) {
        is AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående -> {
            AvkortingVedSøknadsbehandlingDb.Iverksatt.AvkortUtestående(
                avkortingsvarselId = avkortUtestående.id,
            )
        }
        is AvkortingVedSøknadsbehandling.Iverksatt.IngenUtestående -> {
            AvkortingVedSøknadsbehandlingDb.Iverksatt.IngenUtestående
        }
        is AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere -> {
            AvkortingVedSøknadsbehandlingDb.Iverksatt.KanIkkeHåndtere
        }
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
internal sealed class AvkortingVedSøknadsbehandlingDb {

    @JsonSubTypes(
        JsonSubTypes.Type(Uhåndtert.IngenUtestående::class),
        JsonSubTypes.Type(Uhåndtert.UteståendeAvkorting::class),
        JsonSubTypes.Type(Uhåndtert.KanIkkeHåndtere::class),
    )
    sealed class Uhåndtert : AvkortingVedSøknadsbehandlingDb() {
        @JsonTypeName("UHÅNDTERT_INGEN_UTESTÅENDE")
        object IngenUtestående : Uhåndtert()

        @JsonTypeName("UHÅNDTERT_UTESTÅENDE")
        data class UteståendeAvkorting(
            val avkortingsvarselId: UUID,
        ) : Uhåndtert()

        @JsonTypeName("UHÅNDTERT_KAN_IKKE")
        object KanIkkeHåndtere : Uhåndtert()
    }

    @JsonSubTypes(
        JsonSubTypes.Type(Håndtert.IngenUtestående::class),
        JsonSubTypes.Type(Håndtert.AvkortUtestående::class),
        JsonSubTypes.Type(Håndtert.KanIkkeHåndtere::class),
    )
    sealed class Håndtert : AvkortingVedSøknadsbehandlingDb() {

        @JsonTypeName("HÅNDTERT_INGEN_UTESTÅENDE")
        object IngenUtestående : Håndtert()

        @JsonTypeName("HÅNDTERT_AVKORTET_UTESTÅENDE")
        data class AvkortUtestående(
            val avkortingsvarselId: UUID,
        ) : Håndtert()

        @JsonTypeName("HÅNDTERT_KAN_IKKE")
        object KanIkkeHåndtere : Håndtert()
    }

    @JsonSubTypes(
        JsonSubTypes.Type(Iverksatt.IngenUtestående::class),
        JsonSubTypes.Type(Iverksatt.AvkortUtestående::class),
        JsonSubTypes.Type(Iverksatt.KanIkkeHåndtere::class),
    )
    sealed class Iverksatt : AvkortingVedSøknadsbehandlingDb() {
        @JsonTypeName("IVERKSATT_INGEN_UTESTÅENDE")
        object IngenUtestående : Iverksatt()

        @JsonTypeName("IVERKSATT_AVKORTET_UTESTÅENDE")
        data class AvkortUtestående(
            val avkortingsvarselId: UUID,
        ) : Iverksatt()

        @JsonTypeName("IVERKSATT_KAN_IKKE")
        object KanIkkeHåndtere : Iverksatt()
    }
}
