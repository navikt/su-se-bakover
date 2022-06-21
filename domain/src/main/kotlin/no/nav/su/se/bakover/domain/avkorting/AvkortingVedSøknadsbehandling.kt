package no.nav.su.se.bakover.domain.avkorting

import java.util.UUID

/**
 * Representerer tilstander for håndtering av et [Avkortingsvarsel] i kontekst av en søknadsbehandling.
 * Tilstandene sier noe om hvordan den aktuelle søknadsbehandlingen forholder seg til det aktuelle varselet.
 */
sealed class AvkortingVedSøknadsbehandling {

    abstract fun uhåndtert(): Uhåndtert

    /**
     * Tilstand før vi har foretatt oss noe for å håndtere en eventuell utestående avkorting - hvis relevant.
     */
    sealed class Uhåndtert : AvkortingVedSøknadsbehandling() {

        abstract fun håndter(): Håndtert
        abstract override fun uhåndtert(): Uhåndtert
        abstract fun kanIkke(): KanIkkeHåndtere

        /**
         * Vi har identifisert et [Avkortingsvarsel.Utenlandsopphold.SkalAvkortes] som må tas hensyn til.
         */
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

        /**
         * Det er ikke behov for håndtering av noen utestående avkortinger
         */
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

        /**
         * Utestående avkortinger kan ikke håndteres, dette kan f.eks skyldes at søknadsbehandlingen avsluttes.
         */
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

    /**
     * Midlertidig tilstander hvor håndteringen av et [Avkortingsvarsel.Utenlandsopphold.SkalAvkortes] er ferdig.
     */
    sealed class Håndtert : AvkortingVedSøknadsbehandling() {

        abstract override fun uhåndtert(): Uhåndtert
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

        override fun uhåndtert(): Uhåndtert {
            throw IllegalStateException("Kan ikke gå tilbake til uhåndtert etter iverksettelse!")
        }

        /**
         * Represnterer at søknadsbehandlingen har klart å gjennomføre avkorting av et utestående varsel.
         * Som et ledd i denne prosessen oppdateres også status for [avkortingsvarsel].
         *
         * @see Håndtert.AvkortUtestående.iverksett
         * @see Avkortingsvarsel.Utenlandsopphold.Avkortet
         */
        data class AvkortUtestående(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.Avkortet,
        ) : Iverksatt()

        /**
         * Det fantes ingen utestående avkortinger søknadsbehandlingen måtte ta hensyn til.
         */
        object IngenUtestående : Iverksatt()

        /**
         * Søknadsbehandlingen er ikke i stand til å håndtere avkorting. Kan f.eks skyldes at søknadsbehandlingen selv er i en
         * tilstand som er for "tidlig" til at avkortingen er tatt hensyn til og/eller at den er avsluttet.
         */
        data class KanIkkeHåndtere(
            val håndtert: Håndtert,
        ) : Iverksatt()
    }
}
