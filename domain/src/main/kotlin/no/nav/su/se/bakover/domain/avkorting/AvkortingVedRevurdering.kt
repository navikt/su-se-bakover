package no.nav.su.se.bakover.domain.avkorting

import java.util.UUID

sealed class AvkortingVedRevurdering {

    sealed class Uhåndtert : AvkortingVedRevurdering() {

        abstract fun håndter(): DelvisHåndtert
        abstract fun uhåndtert(): Uhåndtert
        abstract fun kanIkke(): KanIkkeHåndtere

        data class UteståendeAvkorting(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
        ) : Uhåndtert() {
            override fun håndter(): DelvisHåndtert.AnnullerUtestående {
                return DelvisHåndtert.AnnullerUtestående(avkortingsvarsel)
            }

            override fun uhåndtert(): Uhåndtert {
                return this
            }

            override fun kanIkke(): KanIkkeHåndtere {
                return KanIkkeHåndtere(this)
            }
        }

        object IngenUtestående : Uhåndtert() {
            override fun håndter(): DelvisHåndtert.IngenUtestående {
                return DelvisHåndtert.IngenUtestående
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
            override fun håndter(): DelvisHåndtert {
                return DelvisHåndtert.KanIkkeHåndtere(uhåndtert.håndter())
            }

            override fun uhåndtert(): Uhåndtert {
                return uhåndtert.uhåndtert()
            }

            override fun kanIkke(): KanIkkeHåndtere {
                return this
            }
        }
    }

    sealed class DelvisHåndtert : AvkortingVedRevurdering() {

        abstract fun håndter(): Håndtert
        abstract fun uhåndtert(): Uhåndtert
        abstract fun kanIkke(): KanIkkeHåndtere

        data class AnnullerUtestående(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
        ) : DelvisHåndtert() {
            override fun håndter(): Håndtert.AnnullerUtestående {
                return Håndtert.AnnullerUtestående(avkortingsvarsel)
            }

            override fun uhåndtert(): Uhåndtert {
                return Uhåndtert.UteståendeAvkorting(avkortingsvarsel)
            }

            override fun kanIkke(): KanIkkeHåndtere {
                return KanIkkeHåndtere(this)
            }
        }

        object IngenUtestående : DelvisHåndtert() {
            override fun håndter(): Håndtert.IngenNyEllerUtestående {
                return Håndtert.IngenNyEllerUtestående
            }

            override fun uhåndtert(): Uhåndtert {
                return Uhåndtert.IngenUtestående
            }

            override fun kanIkke(): KanIkkeHåndtere {
                return KanIkkeHåndtere(this)
            }
        }

        data class KanIkkeHåndtere(
            val delvisHåndtert: DelvisHåndtert,
        ) : DelvisHåndtert() {
            override fun håndter(): Håndtert {
                return Håndtert.KanIkkeHåndteres(delvisHåndtert.håndter())
            }

            override fun uhåndtert(): Uhåndtert {
                return delvisHåndtert.uhåndtert()
            }

            override fun kanIkke(): KanIkkeHåndtere {
                return this
            }
        }
    }

    sealed class Håndtert : AvkortingVedRevurdering() {

        abstract fun uhåndtert(): Uhåndtert
        abstract fun iverksett(behandlingId: UUID): Iverksatt
        abstract fun kanIkke(): KanIkkeHåndteres

        data class OpprettNyttAvkortingsvarselOgAnnullerUtestående(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
            val annullerUtestående: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
        ) : Håndtert() {
            override fun uhåndtert(): Uhåndtert {
                return Uhåndtert.UteståendeAvkorting(annullerUtestående)
            }

            override fun iverksett(behandlingId: UUID): Iverksatt {
                return Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående(
                    avkortingsvarsel,
                    annullerUtestående.annuller(behandlingId),
                )
            }

            override fun kanIkke(): KanIkkeHåndteres {
                return KanIkkeHåndteres(this)
            }
        }

        data class OpprettNyttAvkortingsvarsel(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
        ) : Håndtert() {
            override fun uhåndtert(): Uhåndtert {
                return Uhåndtert.IngenUtestående
            }

            override fun iverksett(behandlingId: UUID): Iverksatt {
                return Iverksatt.OpprettNyttAvkortingsvarsel(avkortingsvarsel)
            }

            override fun kanIkke(): KanIkkeHåndteres {
                return KanIkkeHåndteres(this)
            }
        }

        data class AnnullerUtestående(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
        ) : Håndtert() {
            override fun uhåndtert(): Uhåndtert {
                return Uhåndtert.UteståendeAvkorting(avkortingsvarsel)
            }

            override fun iverksett(behandlingId: UUID): Iverksatt {
                return Iverksatt.AnnullerUtestående(avkortingsvarsel.annuller(behandlingId))
            }

            override fun kanIkke(): KanIkkeHåndteres {
                return KanIkkeHåndteres(this)
            }
        }

        object IngenNyEllerUtestående : Håndtert() {
            override fun uhåndtert(): Uhåndtert {
                return Uhåndtert.IngenUtestående
            }

            override fun iverksett(behandlingId: UUID): Iverksatt {
                return Iverksatt.IngenNyEllerUtestående
            }

            override fun kanIkke(): KanIkkeHåndteres {
                return KanIkkeHåndteres(this)
            }
        }

        data class KanIkkeHåndteres(
            val håndtert: Håndtert,
        ) : Håndtert() {
            override fun uhåndtert(): Uhåndtert {
                return håndtert.uhåndtert()
            }

            override fun iverksett(behandlingId: UUID): Iverksatt {
                return Iverksatt.KanIkkeHåndteres(håndtert)
            }

            override fun kanIkke(): KanIkkeHåndteres {
                return this
            }
        }
    }

    sealed class Iverksatt : AvkortingVedRevurdering() {
        data class OpprettNyttAvkortingsvarselOgAnnullerUtestående(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
            val annullerUtestående: Avkortingsvarsel.Utenlandsopphold.Annullert,
        ) : Iverksatt()

        data class OpprettNyttAvkortingsvarsel(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
        ) : Iverksatt()

        data class AnnullerUtestående(
            val annullerUtestående: Avkortingsvarsel.Utenlandsopphold.Annullert,
        ) : Iverksatt()

        object IngenNyEllerUtestående : Iverksatt()

        data class KanIkkeHåndteres(
            val håndtert: Håndtert,
        ) : Iverksatt()
    }
}
