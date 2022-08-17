package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlin.reflect.KClass

sealed interface Forhåndsvarsel {
    data class UgyldigTilstandsovergang(val fra: KClass<out Forhåndsvarsel>, val til: KClass<out Forhåndsvarsel>)

    fun erKlarForAttestering(): Boolean
    fun harSendtForhåndsvarsel(): Boolean

    fun prøvOvergangTilSkalIkkeForhåndsvarsles(): Either<UgyldigTilstandsovergang, Ferdigbehandlet.SkalIkkeForhåndsvarsles>
    fun prøvOvergangTilSendt(): Either<UgyldigTilstandsovergang, UnderBehandling.Sendt>
    fun prøvOvergangTilEndreGrunnlaget(begrunnelse: String): Either<UgyldigTilstandsovergang, Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget>
    fun prøvOvergangTilFortsettMedSammeGrunnlag(begrunnelse: String): Either<UgyldigTilstandsovergang, Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag>

    /**
     * Denne tilstanden brukes i tilstanden Simulert.
     */
    sealed interface UnderBehandling : Forhåndsvarsel {

        /** Man kan ikke gå fra sendt til IkkeForhåndsvarslet, men andre veien er ok. */
        object Sendt : UnderBehandling {

            override fun erKlarForAttestering() = false
            override fun harSendtForhåndsvarsel() = true

            override fun prøvOvergangTilSkalIkkeForhåndsvarsles() = UgyldigTilstandsovergang(
                fra = this::class,
                til = Ferdigbehandlet.SkalIkkeForhåndsvarsles::class,
            ).left()

            override fun prøvOvergangTilSendt() = UgyldigTilstandsovergang(
                fra = this::class,
                til = Sendt::class,
            ).left()

            override fun prøvOvergangTilEndreGrunnlaget(
                begrunnelse: String,
            ): Either<Nothing, Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget> {
                return Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget(begrunnelse).right()
            }

            override fun prøvOvergangTilFortsettMedSammeGrunnlag(
                begrunnelse: String,
            ): Either<Nothing, Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag> {
                return Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag(begrunnelse).right()
            }
        }
    }

    /**
     * Denne tilstanden brukes når behandlingen er sendt til attestering eller er iverksatt.
     * Den brukes også i tilstandene før, dersom den har blitt underkjent.
     */
    sealed interface Ferdigbehandlet : Forhåndsvarsel {
        override fun erKlarForAttestering() = true

        override fun prøvOvergangTilEndreGrunnlaget(begrunnelse: String) = UgyldigTilstandsovergang(
            fra = this::class,
            til = Forhåndsvarslet.EndreGrunnlaget::class,
        ).left()

        override fun prøvOvergangTilFortsettMedSammeGrunnlag(begrunnelse: String) = UgyldigTilstandsovergang(
            fra = this::class,
            til = Forhåndsvarslet.FortsettMedSammeGrunnlag::class,
        ).left()

        /**
         * Man kan gå i fra tilstanden SkalIkkeForhåndsvarsles til SkalIkkeForhåndsvarsles (idempotent).
         * */
        object SkalIkkeForhåndsvarsles : Ferdigbehandlet {
            override fun harSendtForhåndsvarsel() = false
            override fun prøvOvergangTilSkalIkkeForhåndsvarsles() = SkalIkkeForhåndsvarsles.right()
            override fun prøvOvergangTilSendt() = UnderBehandling.Sendt.right()
        }

        /** Dette er en endelig tilstand */
        sealed interface Forhåndsvarslet : Ferdigbehandlet {
            override fun harSendtForhåndsvarsel() = true
            override fun prøvOvergangTilSkalIkkeForhåndsvarsles() = UgyldigTilstandsovergang(
                fra = this::class,
                til = SkalIkkeForhåndsvarsles::class,
            ).left()
            override fun prøvOvergangTilSendt() = UgyldigTilstandsovergang(
                fra = this::class,
                til = UnderBehandling.Sendt::class,
            ).left()

            /**
             * Gjenstår så lenge 'avsluttet' finnes i databasen.
             * Denne skal kun brukes av ytterpunktene i applikasjonen (database->databaselaget->routes).
             */
            data class Avsluttet(val begrunnelse: String) : Forhåndsvarslet
            data class FortsettMedSammeGrunnlag(val begrunnelse: String) : Forhåndsvarslet
            data class EndreGrunnlaget(val begrunnelse: String) : Forhåndsvarslet
        }
    }
}

fun Forhåndsvarsel?.erKlarForAttestering() = this?.erKlarForAttestering() ?: false
fun Forhåndsvarsel?.harSendtForhåndsvarsel() = this?.harSendtForhåndsvarsel() ?: false

fun Forhåndsvarsel?.prøvOvergangTilSkalIkkeForhåndsvarsles(): Either<Forhåndsvarsel.UgyldigTilstandsovergang, Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles> {
    return this?.prøvOvergangTilSkalIkkeForhåndsvarsles()
        ?: Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles.right()
}

fun Forhåndsvarsel?.prøvOvergangTilSendt(): Either<Forhåndsvarsel.UgyldigTilstandsovergang, Forhåndsvarsel.UnderBehandling.Sendt> {
    return this?.prøvOvergangTilSendt() ?: Forhåndsvarsel.UnderBehandling.Sendt.right()
}

fun Forhåndsvarsel?.prøvOvergangTilEndreGrunnlaget(begrunnelse: String): Either<Forhåndsvarsel.UgyldigTilstandsovergang, Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget> {
    return this?.prøvOvergangTilEndreGrunnlaget(begrunnelse) ?: Forhåndsvarsel.UgyldigTilstandsovergang(
        fra = Nothing::class,
        til = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget::class,
    ).left()
}

fun Forhåndsvarsel?.prøvOvergangTilFortsettMedSammeGrunnlag(begrunnelse: String): Either<Forhåndsvarsel.UgyldigTilstandsovergang, Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag> {
    return this?.prøvOvergangTilFortsettMedSammeGrunnlag(begrunnelse) ?: Forhåndsvarsel.UgyldigTilstandsovergang(
        fra = Nothing::class,
        til = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag::class,
    ).left()
}
