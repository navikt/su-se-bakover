package no.nav.su.se.bakover.database.avkorting

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering

internal fun AvkortingVedRevurdering.toDb(): AvkortingVedRevurderingDb {
    return when (this) {
        is AvkortingVedRevurdering.DelvisHåndtert -> {
            toDb()
        }
        is AvkortingVedRevurdering.Håndtert -> {
            toDb()
        }
        is AvkortingVedRevurdering.Iverksatt -> {
            toDb()
        }
        is AvkortingVedRevurdering.Uhåndtert -> {
            toDb()
        }
    }
}

internal fun AvkortingVedRevurderingDb.toDomain(): AvkortingVedRevurdering {
    return when (this) {
        is AvkortingVedRevurderingDb.DelvisHåndtert -> {
            toDomain()
        }
        is AvkortingVedRevurderingDb.Håndtert -> {
            toDomain()
        }
        is AvkortingVedRevurderingDb.Iverksatt -> {
            toDomain()
        }
        is AvkortingVedRevurderingDb.Uhåndtert -> {
            toDomain()
        }
    }
}

internal fun AvkortingVedRevurderingDb.DelvisHåndtert.toDomain(): AvkortingVedRevurdering.DelvisHåndtert {
    return when (this) {
        is AvkortingVedRevurderingDb.DelvisHåndtert.AnnullerUtestående -> {
            AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående(
                avkortingsvarsel = avkortingsvarsel.toDomain(),
            )
        }
        is AvkortingVedRevurderingDb.DelvisHåndtert.IngenUtestående -> {
            AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående
        }
        is AvkortingVedRevurderingDb.DelvisHåndtert.KanIkkeHåndteres -> {
            AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere(
                delvisHåndtert = delvisHåndtert.toDomain(),
            )
        }
    }
}

internal fun AvkortingVedRevurderingDb.Håndtert.toDomain(): AvkortingVedRevurdering.Håndtert {
    return when (this) {
        is AvkortingVedRevurderingDb.Håndtert.AnnullerUtestående -> {
            AvkortingVedRevurdering.Håndtert.AnnullerUtestående(
                avkortingsvarsel = avkortingsvarsel.toDomain(),
            )
        }
        is AvkortingVedRevurderingDb.Håndtert.IngenNyEllerUtestående -> {
            AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående
        }
        is AvkortingVedRevurderingDb.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
            AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående(
                avkortingsvarsel = avkortingsvarsel.toDomain(),
                annullerUtestående = uteståendeAvkortingsvarsel.toDomain(),
            )
        }
        is AvkortingVedRevurderingDb.Håndtert.OpprettNyttAvkortingsvarsel -> {
            AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel(
                avkortingsvarsel = avkortingsvarsel.toDomain(),
            )
        }
        is AvkortingVedRevurderingDb.Håndtert.KanIkkeHåndteres -> {
            AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres(
                håndtert = håndtert.toDomain(),
            )
        }
    }
}

internal fun AvkortingVedRevurderingDb.Iverksatt.toDomain(): AvkortingVedRevurdering.Iverksatt {
    return when (this) {
        is AvkortingVedRevurderingDb.Iverksatt.AnnullerUtestående -> {
            AvkortingVedRevurdering.Iverksatt.AnnullerUtestående(
                annullerUtestående = avkortingsvarsel.toDomain(),
            )
        }
        is AvkortingVedRevurderingDb.Iverksatt.IngenNyEllerUtestående -> {
            AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående
        }
        is AvkortingVedRevurderingDb.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
            AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående(
                avkortingsvarsel = avkortingsvarsel.toDomain(),
                annullerUtestående = uteståendeAvkortingsvarsel.toDomain(),
            )
        }
        is AvkortingVedRevurderingDb.Iverksatt.OpprettNyttAvkortingsvarsel -> {
            AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel(
                avkortingsvarsel = avkortingsvarsel.toDomain(),
            )
        }
        is AvkortingVedRevurderingDb.Iverksatt.KanIkkeHåndteres -> {
            AvkortingVedRevurdering.Iverksatt.KanIkkeHåndteres(
                håndtert = håndtert.toDomain(),
            )
        }
    }
}

internal fun AvkortingVedRevurderingDb.Uhåndtert.toDomain(): AvkortingVedRevurdering.Uhåndtert {
    return when (this) {
        is AvkortingVedRevurderingDb.Uhåndtert.IngenUtestående -> {
            AvkortingVedRevurdering.Uhåndtert.IngenUtestående
        }
        is AvkortingVedRevurderingDb.Uhåndtert.UteståendeAvkorting -> {
            AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
                avkortingsvarsel = avkortingsvarsel.toDomain(),
            )
        }
        is AvkortingVedRevurderingDb.Uhåndtert.KanIkkeHåndteres -> {
            AvkortingVedRevurdering.Uhåndtert.KanIkkeHåndtere(
                uhåndtert = uhåndtert.toDomain(),
            )
        }
    }
}

internal fun AvkortingVedRevurdering.Uhåndtert.toDb(): AvkortingVedRevurderingDb.Uhåndtert {
    return when (this) {
        is AvkortingVedRevurdering.Uhåndtert.IngenUtestående -> {
            AvkortingVedRevurderingDb.Uhåndtert.IngenUtestående
        }
        is AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting -> {
            AvkortingVedRevurderingDb.Uhåndtert.UteståendeAvkorting(
                avkortingsvarsel = avkortingsvarsel.toDb(),
            )
        }
        is AvkortingVedRevurdering.Uhåndtert.KanIkkeHåndtere -> {
            AvkortingVedRevurderingDb.Uhåndtert.KanIkkeHåndteres(
                uhåndtert = uhåndtert.toDb(),
            )
        }
    }
}

internal fun AvkortingVedRevurdering.DelvisHåndtert.toDb(): AvkortingVedRevurderingDb.DelvisHåndtert {
    return when (this) {
        is AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående -> {
            AvkortingVedRevurderingDb.DelvisHåndtert.AnnullerUtestående(
                avkortingsvarsel = avkortingsvarsel.toDb(),
            )
        }
        is AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående -> {
            AvkortingVedRevurderingDb.DelvisHåndtert.IngenUtestående
        }
        is AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere -> {
            AvkortingVedRevurderingDb.DelvisHåndtert.KanIkkeHåndteres(
                delvisHåndtert = delvisHåndtert.toDb(),
            )
        }
    }
}

internal fun AvkortingVedRevurdering.Håndtert.toDb(): AvkortingVedRevurderingDb.Håndtert {
    return when (this) {
        is AvkortingVedRevurdering.Håndtert.AnnullerUtestående -> {
            AvkortingVedRevurderingDb.Håndtert.AnnullerUtestående(
                avkortingsvarsel = avkortingsvarsel.toDb(),
            )
        }
        is AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående -> {
            AvkortingVedRevurderingDb.Håndtert.IngenNyEllerUtestående
        }
        is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
            AvkortingVedRevurderingDb.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående(
                avkortingsvarsel = avkortingsvarsel.toDb(),
                uteståendeAvkortingsvarsel = annullerUtestående.toDb(),
            )
        }
        is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel -> {
            AvkortingVedRevurderingDb.Håndtert.OpprettNyttAvkortingsvarsel(
                avkortingsvarsel = avkortingsvarsel.toDb(),
            )
        }
        is AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres -> {
            AvkortingVedRevurderingDb.Håndtert.KanIkkeHåndteres(
                håndtert = håndtert.toDb(),
            )
        }
    }
}

internal fun AvkortingVedRevurdering.Iverksatt.toDb(): AvkortingVedRevurderingDb.Iverksatt {
    return when (this) {
        is AvkortingVedRevurdering.Iverksatt.AnnullerUtestående -> {
            AvkortingVedRevurderingDb.Iverksatt.AnnullerUtestående(
                avkortingsvarsel = annullerUtestående.toDb(),
            )
        }
        is AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående -> {
            AvkortingVedRevurderingDb.Iverksatt.IngenNyEllerUtestående
        }
        is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
            AvkortingVedRevurderingDb.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående(
                avkortingsvarsel = avkortingsvarsel.toDb(),
                uteståendeAvkortingsvarsel = annullerUtestående.toDb(),
            )
        }
        is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel -> {
            AvkortingVedRevurderingDb.Iverksatt.OpprettNyttAvkortingsvarsel(
                avkortingsvarsel = avkortingsvarsel.toDb(),
            )
        }
        is AvkortingVedRevurdering.Iverksatt.KanIkkeHåndteres -> {
            AvkortingVedRevurderingDb.Iverksatt.KanIkkeHåndteres(
                håndtert = håndtert.toDb(),
            )
        }
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
internal sealed class AvkortingVedRevurderingDb {

    @JsonSubTypes(
        JsonSubTypes.Type(Uhåndtert.IngenUtestående::class),
        JsonSubTypes.Type(Uhåndtert.UteståendeAvkorting::class),
        JsonSubTypes.Type(Uhåndtert.KanIkkeHåndteres::class),
    )
    sealed class Uhåndtert : AvkortingVedRevurderingDb() {
        @JsonTypeName("UHÅNDTERT_INGEN_UTESTÅENDE")
        data object IngenUtestående : Uhåndtert()

        @JsonTypeName("UHÅNDTERT_UTESTÅENDE")
        data class UteståendeAvkorting(
            val avkortingsvarsel: AvkortingsvarselDb.SkalAvkortes,
        ) : Uhåndtert()

        @JsonTypeName("UHÅNDTERT_KAN_IKKE")
        data class KanIkkeHåndteres(
            val uhåndtert: Uhåndtert,
        ) : Uhåndtert() {
            init {
                require(uhåndtert !is KanIkkeHåndteres)
            }
        }
    }

    @JsonSubTypes(
        JsonSubTypes.Type(DelvisHåndtert.IngenUtestående::class),
        JsonSubTypes.Type(DelvisHåndtert.AnnullerUtestående::class),
        JsonSubTypes.Type(DelvisHåndtert.KanIkkeHåndteres::class),
    )
    sealed class DelvisHåndtert : AvkortingVedRevurderingDb() {
        @JsonTypeName("DELVIS_HÅNDTERT_INGEN_UTESTÅENDE")
        data object IngenUtestående : DelvisHåndtert()

        @JsonTypeName("DELVIS_HÅNDTERT_ANNULLERT_UTESTÅENDE")
        data class AnnullerUtestående(
            val avkortingsvarsel: AvkortingsvarselDb.SkalAvkortes,
        ) : DelvisHåndtert()

        @JsonTypeName("DELVIS_KAN_IKKE")
        data class KanIkkeHåndteres(
            val delvisHåndtert: DelvisHåndtert,
        ) : DelvisHåndtert() {
            init {
                require(delvisHåndtert !is KanIkkeHåndteres)
            }
        }
    }

    @JsonSubTypes(
        JsonSubTypes.Type(Håndtert.IngenNyEllerUtestående::class),
        JsonSubTypes.Type(Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående::class),
        JsonSubTypes.Type(Håndtert.OpprettNyttAvkortingsvarsel::class),
        JsonSubTypes.Type(Håndtert.AnnullerUtestående::class),
        JsonSubTypes.Type(Håndtert.KanIkkeHåndteres::class),
    )
    sealed class Håndtert : AvkortingVedRevurderingDb() {

        @JsonTypeName("HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE")
        data object IngenNyEllerUtestående : Håndtert()

        @JsonTypeName("HÅNDTERT_NY_OG_ANNULLERT_UTESTÅENDE")
        data class OpprettNyttAvkortingsvarselOgAnnullerUtestående(
            val avkortingsvarsel: AvkortingsvarselDb.SkalAvkortes,
            val uteståendeAvkortingsvarsel: AvkortingsvarselDb.SkalAvkortes,
        ) : Håndtert()

        @JsonTypeName("HÅNDTERT_NY")
        data class OpprettNyttAvkortingsvarsel(
            val avkortingsvarsel: AvkortingsvarselDb.SkalAvkortes,
        ) : Håndtert()

        @JsonTypeName("HÅNDTERT_ANNULLERT_UTESTÅENDE")
        data class AnnullerUtestående(
            val avkortingsvarsel: AvkortingsvarselDb.SkalAvkortes,
        ) : Håndtert()

        @JsonTypeName("HÅNDTERT_KAN_IKKE")
        data class KanIkkeHåndteres(
            val håndtert: Håndtert,
        ) : Håndtert() {
            init {
                require(håndtert !is KanIkkeHåndteres)
            }
        }
    }

    @JsonSubTypes(
        JsonSubTypes.Type(Iverksatt.IngenNyEllerUtestående::class),
        JsonSubTypes.Type(Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående::class),
        JsonSubTypes.Type(Iverksatt.OpprettNyttAvkortingsvarsel::class),
        JsonSubTypes.Type(Iverksatt.AnnullerUtestående::class),
        JsonSubTypes.Type(Iverksatt.KanIkkeHåndteres::class),
    )
    sealed class Iverksatt : AvkortingVedRevurderingDb() {
        @JsonTypeName("IVERKSATT_INGEN_NY_ELLER_UTESTÅENDE")
        data object IngenNyEllerUtestående : Iverksatt()

        @JsonTypeName("IVERKSATT_NY_OG_ANNULLERT_UTESTÅENDE")
        data class OpprettNyttAvkortingsvarselOgAnnullerUtestående(
            val avkortingsvarsel: AvkortingsvarselDb.SkalAvkortes,
            val uteståendeAvkortingsvarsel: AvkortingsvarselDb.Annullert,
        ) : Iverksatt()

        @JsonTypeName("IVERKSATT_NY")
        data class OpprettNyttAvkortingsvarsel(
            val avkortingsvarsel: AvkortingsvarselDb.SkalAvkortes,
        ) : Iverksatt()

        @JsonTypeName("IVERKSATT_ANNULLERT_UTESTÅENDE")
        data class AnnullerUtestående(
            val avkortingsvarsel: AvkortingsvarselDb.Annullert,
        ) : Iverksatt()

        @JsonTypeName("IVERKSATT_KAN_IKKE")
        data class KanIkkeHåndteres(
            val håndtert: Håndtert,
        ) : Iverksatt()
    }
}
