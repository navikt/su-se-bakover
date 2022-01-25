package no.nav.su.se.bakover.domain.avkorting

import java.util.UUID

sealed class AvkortingVedSøknadsbehandling {

    sealed class Uhåndtert : AvkortingVedSøknadsbehandling() {

        abstract fun håndter(): Håndtert
        fun kanIkke(): KanIkkeHåndtere {
            return KanIkkeHåndtere
        }

        data class UteståendeAvkorting(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
        ) : Uhåndtert() {
            override fun håndter(): Håndtert.AvkortUtestående {
                return Håndtert.AvkortUtestående(this)
            }
        }

        object IngenUtestående : Uhåndtert() {
            override fun håndter(): Håndtert.IngenUtestående {
                return Håndtert.IngenUtestående
            }
        }

        object KanIkkeHåndtere : Uhåndtert() {
            override fun håndter(): Håndtert.KanIkkeHåndtere {
                return Håndtert.KanIkkeHåndtere
            }
        }
    }

    sealed class Håndtert : AvkortingVedSøknadsbehandling() {

        abstract fun uhåndtert(): Uhåndtert
        abstract fun iverksett(behandlingId: UUID): Iverksatt
        fun kanIkke(): KanIkkeHåndtere {
            return KanIkkeHåndtere
        }

        data class AvkortUtestående(
            val avkortUtestående: Uhåndtert.UteståendeAvkorting,
        ) : Håndtert() {
            override fun uhåndtert(): Uhåndtert {
                return avkortUtestående
            }

            override fun iverksett(behandlingId: UUID): Iverksatt.AvkortUtestående {
                return Iverksatt.AvkortUtestående(
                    avkortUtestående.avkortingsvarsel.avkortet(behandlingId),
                )
            }
        }

        object IngenUtestående : Håndtert() {
            override fun uhåndtert(): Uhåndtert {
                return Uhåndtert.IngenUtestående
            }

            override fun iverksett(behandlingId: UUID): Iverksatt.IngenUtestående {
                return Iverksatt.IngenUtestående
            }
        }

        object KanIkkeHåndtere : Håndtert() {
            override fun uhåndtert(): Uhåndtert {
                return Uhåndtert.KanIkkeHåndtere
            }

            override fun iverksett(behandlingId: UUID): Iverksatt.KanIkkeHåndtere {
                return Iverksatt.KanIkkeHåndtere
            }
        }
    }

    sealed class Iverksatt : AvkortingVedSøknadsbehandling() {
        data class AvkortUtestående(
            val avkortUtestående: Avkortingsvarsel.Utenlandsopphold.Avkortet,
        ) : Iverksatt()

        object IngenUtestående : Iverksatt()

        object KanIkkeHåndtere : Iverksatt()
    }
}
