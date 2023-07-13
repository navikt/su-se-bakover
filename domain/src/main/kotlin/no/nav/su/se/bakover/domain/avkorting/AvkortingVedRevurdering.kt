package no.nav.su.se.bakover.domain.avkorting

import no.nav.su.se.bakover.common.tid.periode.Periode
import java.util.UUID
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Representerer tilstander for håndtering av et eller flere [Avkortingsvarsel] i kontekst av en revurdering.
 * Tilstandene sier noe om hvordan den aktuelle revurdering forholder seg til de aktuelle varslene.
 */
sealed class AvkortingVedRevurdering {

    sealed class Uhåndtert : AvkortingVedRevurdering() {

        abstract fun håndter(): DelvisHåndtert
        abstract fun uhåndtert(): Uhåndtert
        abstract fun kanIkke(): KanIkkeHåndtere

        /**
         * Vi har identifisert et [Avkortingsvarsel.Utenlandsopphold.SkalAvkortes] som må tas hensyn til.
         */
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

        /**
         * Det er ikke behov for håndtering av noen utestående avkortinger
         */
        data object IngenUtestående : Uhåndtert() {
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

        /**
         * Utestående avkortinger kan ikke håndteres, dette kan f.eks skyldes at revurderingen avsluttes.
         */
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

    /**
     * Midlertidig tilstander hvor man er delvis ferdig med håndteringen av [Avkortingsvarsel.Utenlandsopphold.SkalAvkortes].
     * "Delvis" her representerer at man har gjort håndtering utestående varsel, men det kan fortsatt produseres
     * nye varsel som følge av revurderingen som pågår.
     */
    sealed class DelvisHåndtert : AvkortingVedRevurdering() {

        abstract fun håndter(): Håndtert
        abstract fun uhåndtert(): Uhåndtert
        abstract fun kanIkke(): KanIkkeHåndtere

        /**
         * Vi har annullert et utestående varsel.
         */
        data class AnnullerUtestående(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
        ) : DelvisHåndtert() {
            override fun håndter(): Håndtert.AnnullerUtestående {
                return Håndtert.AnnullerUtestående(avkortingsvarsel)
            }

            fun håndter(nyttAvkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes): Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående {
                return Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående(
                    avkortingsvarsel = nyttAvkortingsvarsel,
                    annullerUtestående = avkortingsvarsel,
                )
            }

            override fun uhåndtert(): Uhåndtert {
                return Uhåndtert.UteståendeAvkorting(avkortingsvarsel)
            }

            override fun kanIkke(): KanIkkeHåndtere {
                return KanIkkeHåndtere(this)
            }
        }

        data object IngenUtestående : DelvisHåndtert() {
            override fun håndter(): Håndtert.IngenNyEllerUtestående {
                return Håndtert.IngenNyEllerUtestående
            }

            fun håndter(nyttAvkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes): Håndtert.OpprettNyttAvkortingsvarsel {
                return Håndtert.OpprettNyttAvkortingsvarsel(nyttAvkortingsvarsel)
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

    /**
     * Midlertidig tilstander hvor håndteringen av både utestående og nye varsel er ferdig.
     */
    sealed class Håndtert : AvkortingVedRevurdering() {

        abstract fun uhåndtert(): Uhåndtert
        abstract fun iverksett(behandlingId: UUID): Iverksatt
        abstract fun kanIkke(): KanIkkeHåndteres

        /**
         * Vi har både opprettet et nytt avkortingsvarsel, i tillegg til at vi har annullert det utestående varslet.
         */
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

        data object IngenNyEllerUtestående : Håndtert() {
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

        @OptIn(ExperimentalContracts::class)
        fun skalAvkortes(): Boolean {
            contract {
                returns(true) implies (this@Iverksatt is HarProdusertNyttAvkortingsvarsel)
                returns(false) implies (this@Iverksatt !is HarProdusertNyttAvkortingsvarsel)
            }
            return this is HarProdusertNyttAvkortingsvarsel
        }

        interface HarProdusertNyttAvkortingsvarsel {
            fun avkortingsvarsel(): Avkortingsvarsel.Utenlandsopphold.SkalAvkortes
            fun periode(): Periode
        }

        /**
         * Representerer at revurderingen både har opprettet et nytt [Avkortingsvarsel.Utenlandsopphold], i tillegg
         * til at et utestående [Avkortingsvarsel.Utenlandsopphold] har blitt annullert. Kan oppstå hvis man
         * revurderer en periode med en utestående avkorting. Underveis i prosessen oppdateres også status
         * for de aktuelle varslene.
         *
         * @see Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående.iverksett
         * @see Avkortingsvarsel.Utenlandsopphold.SkalAvkortes
         * @see Avkortingsvarsel.Utenlandsopphold.Annullert
         */
        data class OpprettNyttAvkortingsvarselOgAnnullerUtestående(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
            val annullerUtestående: Avkortingsvarsel.Utenlandsopphold.Annullert,
        ) : Iverksatt(), HarProdusertNyttAvkortingsvarsel {
            override fun avkortingsvarsel(): Avkortingsvarsel.Utenlandsopphold.SkalAvkortes {
                return avkortingsvarsel
            }

            override fun periode(): Periode {
                return avkortingsvarsel.periode()
            }
        }

        /**
         * Revurderingen har endt opp med å produsere et nytt [Avkortingsvarsel.Utenlandsopphold] som skal/kan/bør
         * håndteres av en fremtidig [AvkortingVedSøknadsbehandling].
         *
         * @see Håndtert.OpprettNyttAvkortingsvarsel
         * @see Avkortingsvarsel.Utenlandsopphold.SkalAvkortes
         */
        data class OpprettNyttAvkortingsvarsel(
            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
        ) : Iverksatt(), HarProdusertNyttAvkortingsvarsel {
            override fun avkortingsvarsel(): Avkortingsvarsel.Utenlandsopphold.SkalAvkortes {
                return avkortingsvarsel
            }

            override fun periode(): Periode {
                return avkortingsvarsel.periode()
            }
        }

        /**
         * Revurderingen har endt opp med å annullere et utestående [Avkortingsvarsel.Utenlandsopphold].
         * Kan oppstå hvis man revurderer en periode med utestående varsel, og endrer resultatet slik at det ikke lenger
         * er snakk om en feilutbetaling.
         *
         * @see Håndtert.AnnullerUtestående
         * @see Avkortingsvarsel.Utenlandsopphold.Annullert
         */
        data class AnnullerUtestående(
            val annullerUtestående: Avkortingsvarsel.Utenlandsopphold.Annullert,
        ) : Iverksatt()

        /**
         * Ingen nye varsel er produsert som følge av denne revurderingen. Ei heller er det gjort noen håndtering av
         * utestående varsel.
         */
        data object IngenNyEllerUtestående : Iverksatt()

        /**
         * Revurderinger ikke i stand til å håndtere avkorting. Kan f.eks skyldes at revurdering selv er i en
         * tilstand som er for "tidlig" til at avkortingen er tatt hensyn til og/eller at den er avsluttet.
         */
        data class KanIkkeHåndteres(
            val håndtert: Håndtert,
        ) : Iverksatt()
    }
}
