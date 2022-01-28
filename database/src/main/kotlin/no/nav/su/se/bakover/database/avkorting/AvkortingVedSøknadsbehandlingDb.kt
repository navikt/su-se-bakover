package no.nav.su.se.bakover.database.avkorting

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling

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

internal fun AvkortingVedSøknadsbehandlingDb.toDomain(): AvkortingVedSøknadsbehandling {
    return when (this) {
        is AvkortingVedSøknadsbehandlingDb.Håndtert -> {
            toDomain()
        }
        is AvkortingVedSøknadsbehandlingDb.Iverksatt -> {
            toDomain()
        }
        is AvkortingVedSøknadsbehandlingDb.Uhåndtert -> {
            toDomain()
        }
    }
}

internal fun AvkortingVedSøknadsbehandlingDb.Håndtert.toDomain(): AvkortingVedSøknadsbehandling.Håndtert {
    return when (this) {
        is AvkortingVedSøknadsbehandlingDb.Håndtert.AvkortUtestående -> {
            AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
                avkortingsvarsel = avkortingsvarsel.toDomain(),
            )
        }
        is AvkortingVedSøknadsbehandlingDb.Håndtert.IngenUtestående -> {
            AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående
        }
        is AvkortingVedSøknadsbehandlingDb.Håndtert.KanIkkeHåndtere -> {
            AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere(
                håndtert = håndtert.toDomain(),
            )
        }
    }
}

internal fun AvkortingVedSøknadsbehandlingDb.Iverksatt.toDomain(): AvkortingVedSøknadsbehandling.Iverksatt {
    return when (this) {
        is AvkortingVedSøknadsbehandlingDb.Iverksatt.AvkortUtestående -> {
            AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående(
                avkortingsvarsel = avkortingsvarsel.toDomain(),
            )
        }
        is AvkortingVedSøknadsbehandlingDb.Iverksatt.IngenUtestående -> {
            AvkortingVedSøknadsbehandling.Iverksatt.IngenUtestående
        }
        is AvkortingVedSøknadsbehandlingDb.Iverksatt.KanIkkeHåndtere -> {
            AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
                håndtert = håndtert.toDomain(),
            )
        }
    }
}

internal fun AvkortingVedSøknadsbehandlingDb.Uhåndtert.toDomain(): AvkortingVedSøknadsbehandling.Uhåndtert {
    return when (this) {
        is AvkortingVedSøknadsbehandlingDb.Uhåndtert.IngenUtestående -> {
            AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående
        }
        is AvkortingVedSøknadsbehandlingDb.Uhåndtert.UteståendeAvkorting -> {
            AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
                avkortingsvarsel = avkortingsvarsel.toDomain(),
            )
        }
        is AvkortingVedSøknadsbehandlingDb.Uhåndtert.KanIkkeHåndtere -> {
            AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere(
                uhåndtert = uhåndtert.toDomain(),
            )
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
                avkortingsvarsel = avkortingsvarsel.toDb(),
            )
        }
        is AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere -> {
            AvkortingVedSøknadsbehandlingDb.Uhåndtert.KanIkkeHåndtere(
                uhåndtert = uhåndtert.toDb(),
            )
        }
    }
}

internal fun AvkortingVedSøknadsbehandling.Håndtert.toDb(): AvkortingVedSøknadsbehandlingDb.Håndtert {
    return when (this) {
        is AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående -> {
            AvkortingVedSøknadsbehandlingDb.Håndtert.AvkortUtestående(
                avkortingsvarsel = avkortingsvarsel.toDb(),
            )
        }
        is AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående -> {
            AvkortingVedSøknadsbehandlingDb.Håndtert.IngenUtestående
        }
        is AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere -> {
            AvkortingVedSøknadsbehandlingDb.Håndtert.KanIkkeHåndtere(
                håndtert = håndtert.toDb(),
            )
        }
    }
}

internal fun AvkortingVedSøknadsbehandling.Iverksatt.toDb(): AvkortingVedSøknadsbehandlingDb.Iverksatt {
    return when (this) {
        is AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående -> {
            AvkortingVedSøknadsbehandlingDb.Iverksatt.AvkortUtestående(
                avkortingsvarsel = avkortingsvarsel.toDb(),
            )
        }
        is AvkortingVedSøknadsbehandling.Iverksatt.IngenUtestående -> {
            AvkortingVedSøknadsbehandlingDb.Iverksatt.IngenUtestående
        }
        is AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere -> {
            AvkortingVedSøknadsbehandlingDb.Iverksatt.KanIkkeHåndtere(
                håndtert = håndtert.toDb(),
            )
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
            val avkortingsvarsel: AvkortingsvarselDb.SkalAvkortes,
        ) : Uhåndtert()

        @JsonTypeName("UHÅNDTERT_KAN_IKKE")
        data class KanIkkeHåndtere(
            val uhåndtert: Uhåndtert,
        ) : Uhåndtert() {
            init {
                require(uhåndtert !is KanIkkeHåndtere)
            }
        }
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
            val avkortingsvarsel: AvkortingsvarselDb.SkalAvkortes,
        ) : Håndtert()

        @JsonTypeName("HÅNDTERT_KAN_IKKE")
        data class KanIkkeHåndtere(
            val håndtert: Håndtert,
        ) : Håndtert() {
            init {
                require(håndtert !is KanIkkeHåndtere)
            }
        }
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
            val avkortingsvarsel: AvkortingsvarselDb.Avkortet,
        ) : Iverksatt()

        @JsonTypeName("IVERKSATT_KAN_IKKE")
        data class KanIkkeHåndtere(
            val håndtert: Håndtert,
        ) : Iverksatt()
    }
}
