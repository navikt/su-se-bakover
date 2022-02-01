package no.nav.su.se.bakover.domain.avkorting

import java.util.UUID

sealed class AvkortingVedSøknadsbehandling {

    sealed class Uhåndtert : AvkortingVedSøknadsbehandling() {

        abstract fun håndter(): Håndtert
        abstract fun uhåndtert(): Uhåndtert
        abstract fun kanIkke(): KanIkkeHåndtere

        data class UteståendeAvkorting(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
        ) : Uhåndtert() {
            override fun håndter(): Håndtert.AvkortUtestående {
                return Håndtert.AvkortUtestående(avkortingsvarsel)
            }

            override fun uhåndtert(): Uhåndtert {
                return this
            }

            override fun kanIkke(): KanIkkeHåndtere {
                return KanIkkeHåndtere(this)
            }
        }

        object IngenUtestående : Uhåndtert() {
            override fun håndter(): Håndtert.IngenUtestående {
                return Håndtert.IngenUtestående
            }

            override fun uhåndtert(): Uhåndtert {
                return this
            }

            override fun kanIkke(): KanIkkeHåndtere {
                return KanIkkeHåndtere(this)
            }
        }

        data class KanIkkeHåndtere(
            val uhåndtert: Uhåndtert,
        ) : Uhåndtert() {
            override fun håndter(): Håndtert.KanIkkeHåndtere {
                return Håndtert.KanIkkeHåndtere(uhåndtert.håndter())
            }

            override fun uhåndtert(): Uhåndtert {
                return uhåndtert.uhåndtert()
            }

            override fun kanIkke(): KanIkkeHåndtere {
                return this
            }
        }
    }

    sealed class Håndtert : AvkortingVedSøknadsbehandling() {

        abstract fun uhåndtert(): Uhåndtert
        abstract fun iverksett(behandlingId: UUID): Iverksatt
        abstract fun kanIkke(): KanIkkeHåndtere

        data class AvkortUtestående(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
        ) : Håndtert() {
            override fun uhåndtert(): Uhåndtert {
                return Uhåndtert.UteståendeAvkorting(avkortingsvarsel)
            }

            override fun iverksett(behandlingId: UUID): Iverksatt.AvkortUtestående {
                return Iverksatt.AvkortUtestående(avkortingsvarsel.avkortet(behandlingId))
            }

            override fun kanIkke(): KanIkkeHåndtere {
                return KanIkkeHåndtere(this)
            }
        }

        object IngenUtestående : Håndtert() {
            override fun uhåndtert(): Uhåndtert {
                return Uhåndtert.IngenUtestående
            }

            override fun iverksett(behandlingId: UUID): Iverksatt.IngenUtestående {
                return Iverksatt.IngenUtestående
            }

            override fun kanIkke(): KanIkkeHåndtere {
                return KanIkkeHåndtere(this)
            }
        }

        data class KanIkkeHåndtere(
            val håndtert: Håndtert,
        ) : Håndtert() {
            override fun uhåndtert(): Uhåndtert {
                return håndtert.uhåndtert()
            }

            override fun iverksett(behandlingId: UUID): Iverksatt.KanIkkeHåndtere {
                return Iverksatt.KanIkkeHåndtere(håndtert)
            }

            override fun kanIkke(): KanIkkeHåndtere {
                return KanIkkeHåndtere(håndtert)
            }
        }
    }

    sealed class Iverksatt : AvkortingVedSøknadsbehandling() {
        data class AvkortUtestående(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.Avkortet,
        ) : Iverksatt()

        object IngenUtestående : Iverksatt()

        data class KanIkkeHåndtere(
            val håndtert: Håndtert,
        ) : Iverksatt()
    }
}
