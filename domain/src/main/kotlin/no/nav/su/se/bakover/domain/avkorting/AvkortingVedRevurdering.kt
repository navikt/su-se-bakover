package no.nav.su.se.bakover.domain.avkorting

import java.util.UUID

sealed class AvkortingVedRevurdering {

    sealed class Uhåndtert : AvkortingVedRevurdering() {

        abstract fun håndter(): DelvisHåndtert
        fun kanIkke(): Uhåndtert {
            return KanIkkeHåndtere
        }

        data class UteståendeAvkorting(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
        ) : Uhåndtert() {
            override fun håndter(): DelvisHåndtert.AnnullerUtestående {
                return DelvisHåndtert.AnnullerUtestående(this)
            }
        }

        object IngenUtestående : Uhåndtert() {
            override fun håndter(): DelvisHåndtert.IngenUtestående {
                return DelvisHåndtert.IngenUtestående
            }
        }

        object KanIkkeHåndtere : Uhåndtert() {
            override fun håndter(): DelvisHåndtert {
                return DelvisHåndtert.KanIkkeHåndtere
            }
        }
    }

    sealed class DelvisHåndtert : AvkortingVedRevurdering() {

        abstract fun håndter(): Håndtert
        abstract fun uhåndtert(): Uhåndtert
        fun kanIkke(): DelvisHåndtert {
            return KanIkkeHåndtere
        }

        data class AnnullerUtestående(
            val uteståendeAvkorting: Uhåndtert.UteståendeAvkorting,
        ) : DelvisHåndtert() {
            override fun håndter(): Håndtert.AnnullerUtestående {
                return Håndtert.AnnullerUtestående(this)
            }

            override fun uhåndtert(): Uhåndtert {
                return Uhåndtert.UteståendeAvkorting(uteståendeAvkorting.avkortingsvarsel)
            }
        }

        object IngenUtestående : DelvisHåndtert() {
            override fun håndter(): Håndtert.IngenNyEllerUtestående {
                return Håndtert.IngenNyEllerUtestående
            }

            override fun uhåndtert(): Uhåndtert {
                return Uhåndtert.IngenUtestående
            }
        }

        object KanIkkeHåndtere : DelvisHåndtert() {
            override fun håndter(): Håndtert {
                return Håndtert.KanIkkeHåndteres
            }

            override fun uhåndtert(): Uhåndtert {
                return Uhåndtert.KanIkkeHåndtere
            }
        }
    }

    sealed class Håndtert : AvkortingVedRevurdering() {

        abstract fun uhåndtert(): Uhåndtert
        abstract fun iverksett(behandlingId: UUID): Iverksatt
        fun kanIkke(): Håndtert {
            return KanIkkeHåndteres
        }

        data class OpprettNyttAvkortingsvarselOgAnnullerUtestående(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
            val annullerUtestående: DelvisHåndtert.AnnullerUtestående,
        ) : Håndtert() {
            override fun uhåndtert(): Uhåndtert {
                return annullerUtestående.uhåndtert()
            }

            override fun iverksett(behandlingId: UUID): Iverksatt {
                return Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående(
                    avkortingsvarsel,
                    annullerUtestående.uteståendeAvkorting.avkortingsvarsel.annuller(behandlingId),
                )
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
        }

        data class AnnullerUtestående(
            val annullerUtestående: DelvisHåndtert.AnnullerUtestående,
        ) : Håndtert() {
            override fun uhåndtert(): Uhåndtert {
                return annullerUtestående.uhåndtert()
            }

            override fun iverksett(behandlingId: UUID): Iverksatt {
                return Iverksatt.AnnullerUtestående(
                    annullerUtestående.uteståendeAvkorting.avkortingsvarsel.annuller(
                        behandlingId,
                    ),
                )
            }
        }

        object IngenNyEllerUtestående : Håndtert() {
            override fun uhåndtert(): Uhåndtert {
                return Uhåndtert.IngenUtestående
            }

            override fun iverksett(behandlingId: UUID): Iverksatt {
                return Iverksatt.IngenNyEllerUtestående
            }
        }

        object KanIkkeHåndteres : Håndtert() {
            override fun uhåndtert(): Uhåndtert {
                return Uhåndtert.KanIkkeHåndtere
            }

            override fun iverksett(behandlingId: UUID): Iverksatt {
                return Iverksatt.KanIkkeHåndteres
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

        object KanIkkeHåndteres : Iverksatt()
    }
}
