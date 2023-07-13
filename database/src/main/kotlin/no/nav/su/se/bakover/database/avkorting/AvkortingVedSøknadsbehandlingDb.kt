package no.nav.su.se.bakover.database.avkorting

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import no.nav.su.se.bakover.common.deserializeNullable
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling

internal fun AvkortingVedSøknadsbehandling.toDbJson(): String? {
    return when (this) {
        is AvkortingVedSøknadsbehandling.Avkortet -> AvkortingVedSøknadsbehandlingDb.Avkortet(
            avkortingsvarsel = avkortingsvarsel.toDb(),
        )

        is AvkortingVedSøknadsbehandling.IngenAvkorting -> null
        is AvkortingVedSøknadsbehandling.SkalAvkortes -> AvkortingVedSøknadsbehandlingDb.SkalAvkortes(
            avkortingsvarsel = avkortingsvarsel.toDb(),
        )
        is AvkortingVedSøknadsbehandling.IkkeVurdert -> null
    }?.let { serialize(it) }
}

internal fun fromAvkortingDbJson(dbJson: String?): AvkortingVedSøknadsbehandling? {
    return deserializeNullable<AvkortingVedSøknadsbehandlingDb>(dbJson)?.toDomain()
}

internal fun AvkortingVedSøknadsbehandlingDb.toDomain(): AvkortingVedSøknadsbehandling {
    return when (this) {
        is AvkortingVedSøknadsbehandlingDb.Håndtert -> toDomain()
        is AvkortingVedSøknadsbehandlingDb.Iverksatt -> toDomain()
        is AvkortingVedSøknadsbehandlingDb.Uhåndtert -> AvkortingVedSøknadsbehandling.IkkeVurdert
        is AvkortingVedSøknadsbehandlingDb.Avkortet -> AvkortingVedSøknadsbehandling.Avkortet(
            avkortingsvarsel = avkortingsvarsel.toDomain(),
        )
        is AvkortingVedSøknadsbehandlingDb.SkalAvkortes -> AvkortingVedSøknadsbehandling.SkalAvkortes(
            avkortingsvarsel = avkortingsvarsel.toDomain(),
        )
    }
}

internal fun AvkortingVedSøknadsbehandlingDb.Håndtert.toDomain(): AvkortingVedSøknadsbehandling.Vurdert {
    return when (this) {
        is AvkortingVedSøknadsbehandlingDb.Håndtert.AvkortUtestående -> {
            AvkortingVedSøknadsbehandling.SkalAvkortes(
                avkortingsvarsel = avkortingsvarsel.toDomain(),
            )
        }

        is AvkortingVedSøknadsbehandlingDb.Håndtert.IngenUtestående -> {
            AvkortingVedSøknadsbehandling.IngenAvkorting
        }

        is AvkortingVedSøknadsbehandlingDb.Håndtert.KanIkkeHåndtere -> {
            // I disse tilfellene vil søknadsbehandlingen være lukket.
            AvkortingVedSøknadsbehandling.IngenAvkorting
        }
    }
}

internal fun AvkortingVedSøknadsbehandlingDb.Iverksatt.toDomain(): AvkortingVedSøknadsbehandling.Vurdert {
    return when (this) {
        is AvkortingVedSøknadsbehandlingDb.Iverksatt.AvkortUtestående -> {
            AvkortingVedSøknadsbehandling.Avkortet(
                avkortingsvarsel = avkortingsvarsel.toDomain(),
            )
        }

        is AvkortingVedSøknadsbehandlingDb.Iverksatt.IngenUtestående -> {
            AvkortingVedSøknadsbehandling.IngenAvkorting
        }

        is AvkortingVedSøknadsbehandlingDb.Iverksatt.KanIkkeHåndtere -> {
            throw IllegalStateException("Avventer migrering av AvkortingVedSøknadsbehandlingDb.Iverksatt.KanIkkeHåndtere - skal ikke være i bruk.")
        }
    }
}

/**
 * Typene til Søknadsbehandling styrer hva slags type avkorting skal være, så vi sitter igjen med null, skal avkortes og avkortet.
 * En kunne argumentert for at Avkortet og SkalAvkortes har de samme dataene, men ønsker ikke endre på [AvkortingsvarselDb].
 *
 * TODO jah: Vurder om vi skal la nye/gamle typer leve om hverandre, eller om vi skal migrere de gamle. Dette kan gjøres senere.
 * TODO jah: På sikt, fjern alt annet enn AVKORTET og SKAL_AVKORTES.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
internal sealed class AvkortingVedSøknadsbehandlingDb {

    @JsonTypeName("AVKORTET")
    data class Avkortet(
        val avkortingsvarsel: AvkortingsvarselDb.Avkortet,
    ) : AvkortingVedSøknadsbehandlingDb()

    @JsonTypeName("SKAL_AVKORTES")
    data class SkalAvkortes(
        val avkortingsvarsel: AvkortingsvarselDb.SkalAvkortes,
    ) : AvkortingVedSøknadsbehandlingDb()

    /**
     * TODO jah: Fjern og/eller migrer vekk fra denne.
     *
     * Legger ved en oversikt over legacy kombinasjoner mellom status og avkorting->>'@type' fra produksjon+preprod 2023-05-24
     * Det er verdt å merke at søknadsbehandlinger som er lukket vil få @type *_KAN_IKKE
     * status=OPPRETTET,                 legacy avkortingstype=UHÅNDTERT_KAN_IKKE,            ny avkortingstype=IkkeVurdert (ignorerer avkorting)
     * status=VILKÅRSVURDERT_AVSLAG,     legacy avkortingstype=UHÅNDTERT_KAN_IKKE,            ny avkortingstype=IkkeVurdert (ignorerer avkorting)
     * status=VILKÅRSVURDERT_INNVILGET,  legacy avkortingstype=UHÅNDTERT_KAN_IKKE,            ny avkortingstype=IkkeVurdert (ignorerer avkorting)
     * status=VILKÅRSVURDERT_INNVILGET,  legacy avkortingstype=UHÅNDTERT_INGEN_UTESTÅENDE,    ny avkortingstype=IkkeVurdert (ignorerer avkorting)
     * status=SIMULERT,                  legacy avkortingstype=HÅNDTERT_KAN_IKKE,             ny avkortingstype=IngenAvkorting (vil være lukket)
     * status=TIL_ATTESTERING_INNVILGET, legacy avkortingstype=HÅNDTERT_INGEN_UTESTÅENDE,     ny avkortingstype=IngenAvkorting
     * status=TIL_ATTESTERING_AVSLAG,    legacy avkortingstype=HÅNDTERT_KAN_IKKE,             ny avkortingstype=IngenAvkorting (ignorerer avkorting)
     * status=IVERKSATT_INNVILGET,       legacy avkortingstype=IVERKSATT_AVKORTET_UTESTÅENDE, ny avkortingstype=Avkortet
     * status=IVERKSATT_AVSLAG,          legacy avkortingstype=IVERKSATT_KAN_IKKE,            ny avkortingstype=IngenAvkorting (ignorerer avkorting)
     * status=IVERKSATT_INNVILGET,       legacy avkortingstype=IVERKSATT_INGEN_UTESTÅENDE,    ny avkortingstype=IngenAvkorting
     *
     * Unike kombinasjoner fra preprod som ikke finnes i prod:
     * status=VILKÅRSVURDERT_INNVILGET,  legacy avkortingstype=UHÅNDTERT_UTESTÅENDE,          ny avkortingstype=IkkeVurdert (ignorerer avkorting)
     * status=BEREGNET_INNVILGET,        legacy avkortingstype=HÅNDTERT_KAN_IKKE,             ny avkortingstype=IngenAvkorting (vil være lukket)
     * status=BEREGNET_INNVILGET,        legacy avkortingstype=HÅNDTERT_INGEN_UTESTÅENDE,     ny avkortingstype=IngenAvkorting
     * status=SIMULERT,                  legacy avkortingstype=HÅNDTERT_INGEN_UTESTÅENDE,     ny avkortingstype=IngenAvkorting
     * status=UNDERKJENT_INNVILGET,      legacy avkortingstype=HÅNDTERT_INGEN_UTESTÅENDE,     ny avkortingstype=IngenAvkorting
     * */
    @JsonSubTypes(
        JsonSubTypes.Type(Uhåndtert.IngenUtestående::class),
        JsonSubTypes.Type(Uhåndtert.UteståendeAvkorting::class),
        JsonSubTypes.Type(Uhåndtert.KanIkkeHåndtere::class),
    )
    sealed class Uhåndtert : AvkortingVedSøknadsbehandlingDb() {
        @JsonTypeName("UHÅNDTERT_INGEN_UTESTÅENDE")
        data object IngenUtestående : Uhåndtert()

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

    /** TODO jah: Fjern og/eller migrer vekk fra denne. */
    @JsonSubTypes(
        JsonSubTypes.Type(Håndtert.IngenUtestående::class),
        JsonSubTypes.Type(Håndtert.AvkortUtestående::class),
        JsonSubTypes.Type(Håndtert.KanIkkeHåndtere::class),
    )
    sealed class Håndtert : AvkortingVedSøknadsbehandlingDb() {

        @JsonTypeName("HÅNDTERT_INGEN_UTESTÅENDE")
        data object IngenUtestående : Håndtert()

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

    /** TODO jah: Fjern og/eller migrer vekk fra denne. */
    @JsonSubTypes(
        JsonSubTypes.Type(Iverksatt.IngenUtestående::class),
        JsonSubTypes.Type(Iverksatt.AvkortUtestående::class),
        JsonSubTypes.Type(Iverksatt.KanIkkeHåndtere::class),
    )
    sealed class Iverksatt : AvkortingVedSøknadsbehandlingDb() {
        @JsonTypeName("IVERKSATT_INGEN_UTESTÅENDE")
        data object IngenUtestående : Iverksatt()

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
