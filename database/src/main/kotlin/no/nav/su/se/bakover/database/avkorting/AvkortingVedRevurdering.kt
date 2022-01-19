package no.nav.su.se.bakover.database.avkorting

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import no.nav.su.se.bakover.database.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import java.util.UUID

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

internal fun AvkortingVedRevurderingDb.toDomain(
    avkortingsvarselPostgresRepo: AvkortingsvarselPostgresRepo,
    session: Session,
): AvkortingVedRevurdering {
    return when (this) {
        is AvkortingVedRevurderingDb.DelvisHåndtert -> {
            toDomain(avkortingsvarselPostgresRepo, session)
        }
        is AvkortingVedRevurderingDb.Håndtert -> {
            toDomain(avkortingsvarselPostgresRepo, session)
        }
        is AvkortingVedRevurderingDb.Iverksatt -> {
            toDomain(avkortingsvarselPostgresRepo, session)
        }
        is AvkortingVedRevurderingDb.Uhåndtert -> {
            toDomain(avkortingsvarselPostgresRepo, session)
        }
    }
}

internal fun AvkortingVedRevurderingDb.DelvisHåndtert.toDomain(
    avkortingsvarselPostgresRepo: AvkortingsvarselPostgresRepo,
    session: Session,
): AvkortingVedRevurdering.DelvisHåndtert {
    return when (this) {
        is AvkortingVedRevurderingDb.DelvisHåndtert.AnnullerUtestående -> {
            AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående(
                AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
                    avkortingsvarsel = avkortingsvarselPostgresRepo.hent(
                        id = avkortingsvarselId,
                        session = session,
                    ) as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
                ),
            )
        }
        is AvkortingVedRevurderingDb.DelvisHåndtert.IngenUtestående -> {
            AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående
        }
        is AvkortingVedRevurderingDb.DelvisHåndtert.KanIkkeHåndteres -> {
            AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere
        }
    }
}

internal fun AvkortingVedRevurderingDb.Håndtert.toDomain(
    avkortingsvarselPostgresRepo: AvkortingsvarselPostgresRepo,
    session: Session,
): AvkortingVedRevurdering.Håndtert {
    return when (this) {
        is AvkortingVedRevurderingDb.Håndtert.AnnullerUtestående -> {
            AvkortingVedRevurdering.Håndtert.AnnullerUtestående(
                AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående(
                    AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
                        avkortingsvarsel = avkortingsvarselPostgresRepo.hent(
                            id = avkortingsvarselId,
                            session = session,
                        ) as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
                    ),
                ),
            )
        }
        is AvkortingVedRevurderingDb.Håndtert.IngenNyEllerUtestående -> {
            AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående
        }
        is AvkortingVedRevurderingDb.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
            AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående(
                avkortingsvarsel = avkortingsvarsel.toDomain() as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
                annullerUtestående = AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående(
                    AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
                        avkortingsvarsel = avkortingsvarselPostgresRepo.hent(
                            id = avkortingsvarselId,
                            session = session,
                        ) as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
                    ),
                ),
            )
        }
        is AvkortingVedRevurderingDb.Håndtert.OpprettNyttAvkortingsvarsel -> {
            AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel(
                avkortingsvarsel = avkortingsvarsel.toDomain() as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
            )
        }
        is AvkortingVedRevurderingDb.Håndtert.KanIkkeHåndteres -> {
            AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres
        }
    }
}

internal fun AvkortingVedRevurderingDb.Iverksatt.toDomain(
    avkortingsvarselPostgresRepo: AvkortingsvarselPostgresRepo,
    session: Session,
): AvkortingVedRevurdering.Iverksatt {
    return when (this) {
        is AvkortingVedRevurderingDb.Iverksatt.AnnullerUtestående -> {
            AvkortingVedRevurdering.Iverksatt.AnnullerUtestående(
                annullerUtestående = avkortingsvarselPostgresRepo.hent(
                    id = avkortingsvarselId,
                    session = session,
                ) as Avkortingsvarsel.Utenlandsopphold.Annullert,
            )
        }
        is AvkortingVedRevurderingDb.Iverksatt.IngenNyEllerUtestående -> {
            AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående
        }
        is AvkortingVedRevurderingDb.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
            AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående(
                avkortingsvarsel = avkortingsvarselPostgresRepo.hent(
                    id = avkortingsvarsel.id,
                    session = session,
                ) as Avkortingsvarsel.Utenlandsopphold,
                annullerUtestående = avkortingsvarselPostgresRepo.hent(
                    id = avkortingsvarselId,
                    session = session,
                ) as Avkortingsvarsel.Utenlandsopphold.Annullert,
            )
        }
        is AvkortingVedRevurderingDb.Iverksatt.OpprettNyttAvkortingsvarsel -> {
            AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel(
                avkortingsvarsel = avkortingsvarselPostgresRepo.hent(
                    id = avkortingsvarsel.id,
                    session = session,
                ) as Avkortingsvarsel.Utenlandsopphold,
            )
        }
        is AvkortingVedRevurderingDb.Iverksatt.KanIkkeHåndteres -> {
            AvkortingVedRevurdering.Iverksatt.KanIkkeHåndteres
        }
    }
}

internal fun AvkortingVedRevurderingDb.Uhåndtert.toDomain(
    avkortingsvarselPostgresRepo: AvkortingsvarselPostgresRepo,
    session: Session,
): AvkortingVedRevurdering.Uhåndtert {
    return when (this) {
        is AvkortingVedRevurderingDb.Uhåndtert.IngenUtestående -> {
            AvkortingVedRevurdering.Uhåndtert.IngenUtestående
        }
        is AvkortingVedRevurderingDb.Uhåndtert.UteståendeAvkorting -> {
            AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
                avkortingsvarsel = avkortingsvarselPostgresRepo.hent(
                    id = avkortingsvarselId,
                    session = session,
                ) as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
            )
        }
        is AvkortingVedRevurderingDb.Uhåndtert.KanIkkeHåndteres -> {
            AvkortingVedRevurdering.Uhåndtert.KanIkkeHåndtere
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
                avkortingsvarselId = avkortingsvarsel.id,
            )
        }
        is AvkortingVedRevurdering.Uhåndtert.KanIkkeHåndtere -> {
            AvkortingVedRevurderingDb.Uhåndtert.KanIkkeHåndteres
        }
    }
}

internal fun AvkortingVedRevurdering.DelvisHåndtert.toDb(): AvkortingVedRevurderingDb.DelvisHåndtert {
    return when (this) {
        is AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående -> {
            AvkortingVedRevurderingDb.DelvisHåndtert.AnnullerUtestående(
                avkortingsvarselId = uteståendeAvkorting.avkortingsvarsel.id,
            )
        }
        is AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående -> {
            AvkortingVedRevurderingDb.DelvisHåndtert.IngenUtestående
        }
        is AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere -> {
            AvkortingVedRevurderingDb.DelvisHåndtert.KanIkkeHåndteres
        }
    }
}

internal fun AvkortingVedRevurdering.Håndtert.toDb(): AvkortingVedRevurderingDb.Håndtert {
    return when (this) {
        is AvkortingVedRevurdering.Håndtert.AnnullerUtestående -> {
            AvkortingVedRevurderingDb.Håndtert.AnnullerUtestående(
                avkortingsvarselId = annullerUtestående.uteståendeAvkorting.avkortingsvarsel.id,
            )
        }
        is AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående -> {
            AvkortingVedRevurderingDb.Håndtert.IngenNyEllerUtestående
        }
        is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
            AvkortingVedRevurderingDb.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående(
                avkortingsvarsel = avkortingsvarsel.toDb(),
                avkortingsvarselId = annullerUtestående.uteståendeAvkorting.avkortingsvarsel.id,
            )
        }
        is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel -> {
            AvkortingVedRevurderingDb.Håndtert.OpprettNyttAvkortingsvarsel(
                avkortingsvarsel = avkortingsvarsel.toDb(),
            )
        }
        is AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres -> {
            AvkortingVedRevurderingDb.Håndtert.KanIkkeHåndteres
        }
    }
}

internal fun AvkortingVedRevurdering.Iverksatt.toDb(): AvkortingVedRevurderingDb.Iverksatt {
    return when (this) {
        is AvkortingVedRevurdering.Iverksatt.AnnullerUtestående -> {
            AvkortingVedRevurderingDb.Iverksatt.AnnullerUtestående(
                avkortingsvarselId = annullerUtestående.id,
            )
        }
        is AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående -> {
            AvkortingVedRevurderingDb.Iverksatt.IngenNyEllerUtestående
        }
        is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
            AvkortingVedRevurderingDb.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående(
                avkortingsvarsel = avkortingsvarsel.toDb(),
                avkortingsvarselId = annullerUtestående.id,
            )
        }
        is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel -> {
            AvkortingVedRevurderingDb.Iverksatt.OpprettNyttAvkortingsvarsel(
                avkortingsvarsel = avkortingsvarsel.toDb(),
            )
        }
        is AvkortingVedRevurdering.Iverksatt.KanIkkeHåndteres -> {
            AvkortingVedRevurderingDb.Iverksatt.KanIkkeHåndteres
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
        object IngenUtestående : Uhåndtert()

        @JsonTypeName("UHÅNDTERT_UTESTÅENDE")
        data class UteståendeAvkorting(
            val avkortingsvarselId: UUID,
        ) : Uhåndtert()

        @JsonTypeName("UHÅNDTERT_KAN_IKKE")
        object KanIkkeHåndteres : Uhåndtert()
    }

    @JsonSubTypes(
        JsonSubTypes.Type(DelvisHåndtert.IngenUtestående::class),
        JsonSubTypes.Type(DelvisHåndtert.AnnullerUtestående::class),
        JsonSubTypes.Type(DelvisHåndtert.KanIkkeHåndteres::class),
    )
    sealed class DelvisHåndtert : AvkortingVedRevurderingDb() {
        @JsonTypeName("DELVIS_HÅNDTERT_INGEN_UTESTÅENDE")
        object IngenUtestående : DelvisHåndtert()

        @JsonTypeName("DELVIS_HÅNDTERT_ANNULLERT_UTESTÅENDE")
        data class AnnullerUtestående(
            val avkortingsvarselId: UUID,
        ) : DelvisHåndtert()

        @JsonTypeName("DELVIS_KAN_IKKE")
        object KanIkkeHåndteres : DelvisHåndtert()
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
        object IngenNyEllerUtestående : Håndtert()

        @JsonTypeName("HÅNDTERT_NY_OG_ANNULLERT_UTESTÅENDE")
        data class OpprettNyttAvkortingsvarselOgAnnullerUtestående(
            val avkortingsvarsel: AvkortingsvarselDb,
            val avkortingsvarselId: UUID,
        ) : Håndtert()

        @JsonTypeName("HÅNDTERT_NY")
        data class OpprettNyttAvkortingsvarsel(
            val avkortingsvarsel: AvkortingsvarselDb,
        ) : Håndtert()

        @JsonTypeName("HÅNDTRERT_ANNULLERT_UTESTÅENDE")
        data class AnnullerUtestående(
            val avkortingsvarselId: UUID,
        ) : Håndtert()

        @JsonTypeName("HÅNDTERT_KAN_IKKE")
        object KanIkkeHåndteres : Håndtert()
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
        object IngenNyEllerUtestående : Iverksatt()

        @JsonTypeName("IVERKSATT_NY_OG_ANNULLERT_UTESTÅENDE")
        data class OpprettNyttAvkortingsvarselOgAnnullerUtestående(
            val avkortingsvarsel: AvkortingsvarselDb,
            val avkortingsvarselId: UUID,
        ) : Iverksatt()

        @JsonTypeName("IVERKSATT_NY")
        data class OpprettNyttAvkortingsvarsel(
            val avkortingsvarsel: AvkortingsvarselDb,
        ) : Iverksatt()

        @JsonTypeName("IVERKSATT_ANNULLERT_UTESTÅENDE")
        data class AnnullerUtestående(
            val avkortingsvarselId: UUID,
        ) : Iverksatt()

        @JsonTypeName("IVERKSATT_KAN_IKKE")
        object KanIkkeHåndteres : Iverksatt()
    }
}
