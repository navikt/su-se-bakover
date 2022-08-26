package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.Periode.UgyldigPeriode
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeForhåndsvarsle
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.routes.Feilresponser.Brev.kunneIkkeLageBrevutkast
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkePerson
import no.nav.su.se.bakover.web.routes.Feilresponser.feilVedGenereringAvDokument
import no.nav.su.se.bakover.web.routes.Feilresponser.kunneIkkeOppretteOppgave
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.Brev.brevvalgIkkeTillatt
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.Brev.fantIkkeGjeldendeUtbetaling
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.Brev.navneoppslagSaksbehandlerAttesttantFeilet

internal object Revurderingsfeilresponser {
    val fantIkkeSak = NotFound.errorJson(
        "Fant ikke sak",
        "fant_ikke_sak",
    )

    val fantIkkeRevurdering = NotFound.errorJson(
        "Fant ikke revurdering",
        "fant_ikke_revurdering",
    )

    val fantIngenVedtakSomKanRevurderes = NotFound.errorJson(
        "Fant ingen vedtak som kan revurderes for angitt periode",
        "ingenting_å_revurdere_i_perioden",
    )

    val fantIkkePersonEllerSaksbehandlerNavn = BadRequest.errorJson(
        "Fant ikke person eller saksbehandler navn",
        "fant_ikke_person_eller_saksbehandler_navn",
    )

    object OpprettelseOgOppdateringAvRevurdering {
        val måVelgeInformasjonSomRevurderes = BadRequest.errorJson(
            "Må velge minst en ting som skal revurderes",
            "må_velge_informasjon_som_revurderes",
        )
        val tidslinjeForVedtakErIkkeKontinuerlig = InternalServerError.errorJson(
            "Mangler systemstøtte for revurdering av perioder med hull i tidslinjen for vedtak",
            "tidslinje_for_vedtak_ikke_kontinuerlig",
        )
        val begrunnelseKanIkkeVæreTom = BadRequest.errorJson(
            "Begrunnelse kan ikke være tom",
            "begrunnelse_kan_ikke_være_tom",
        )
        val ugyldigÅrsak = BadRequest.errorJson(
            "Ugyldig årsak, må være en av: ${Revurderingsårsak.Årsak.values()}",
            "ugyldig_årsak",
        )
        val formueSomFørerTilOpphørMåRevurderes = BadRequest.errorJson(
            "Formue som fører til opphør må revurderes",
            "formue_som_fører_til_opphør_må_revurderes",
        )
        val utenlandsoppholdSomFørerTilOpphørMåRevurderes = BadRequest.errorJson(
            "Utenlandsopphold som fører til opphør må revurderes",
            "utenlandsopphold_som_fører_til_opphør_må_revurderes",
        )

        val epsFormueMedFlereBosituasjonsperioderMåRevurderes = BadRequest.errorJson(
            "Formue må revurderes siden det finnes EPS formue og flere bosituasjonsperioder",
            "eps_formue_med_flere_perioder_må_revurderes",
        )

        fun uteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(periode: Periode) = InternalServerError.errorJson(
            "Saken har en utestående avkorting som enten må avkortes i ny stønadsperiode eller revurderes i sin helhet. Vennligst inkluder ${periode.fraOgMed}-${periode.tilOgMed} i revurderingsperioden eller avkort i ny stønadsperiode.",
            "utestående_avkorting_må_revurderes_eller_avkortes_i_ny_periode",
        )
    }

    object Brev {
        val navneoppslagSaksbehandlerAttesttantFeilet = InternalServerError.errorJson(
            "Kunne ikke hente navn for saksbehandler eller attestant",
            "navneoppslag_feilet",
        )

        val fantIkkeGjeldendeUtbetaling = InternalServerError.errorJson(
            "Kunne ikke hente gjeldende utbetaling",
            "kunne_ikke_hente_gjeldende_utbetaling",
        )
        val brevvalgIkkeTillatt = BadRequest.errorJson(
            "Brevvalg ikke tillatt",
            "brevvalg_ikke_tillatt",
        )
        val manglerBrevvalg = BadRequest.errorJson(
            "Mangler brevvalg",
            "mangler_brevvalg",
        )
    }

    fun ugyldigPeriode(ugyldigPeriode: UgyldigPeriode): Resultat {
        return BadRequest.errorJson(
            ugyldigPeriode.toString(),
            "ugyldig_periode",
        )
    }

    fun KunneIkkeForhåndsvarsle.tilResultat() = when (this) {
        is KunneIkkeForhåndsvarsle.UgyldigTilstandsovergangForForhåndsvarsling -> HttpStatusCode.Conflict.errorJson(
            "Allerede forhåndsvarslet",
            "allerede_forhåndsvarslet",
        )

        is KunneIkkeForhåndsvarsle.FantIkkePerson -> fantIkkePerson
        is KunneIkkeForhåndsvarsle.KunneIkkeOppdatereOppgave -> kunneIkkeOppretteOppgave
        is KunneIkkeForhåndsvarsle.FantIkkeRevurdering -> fantIkkeRevurdering
        is KunneIkkeForhåndsvarsle.MåVæreITilstandenSimulert -> BadRequest.errorJson(
            "Må være i tilstanden ${SimulertRevurdering::class.simpleName} for å kunne forhåndsvarsle. Nåværende tilstand: ${fra.simpleName} ",
            "ugyldig_tilstand",
        )

        is KunneIkkeForhåndsvarsle.Attestering -> this.subError.tilResultat()
        is KunneIkkeForhåndsvarsle.KunneIkkeHenteNavnForSaksbehandler -> navneoppslagSaksbehandlerAttesttantFeilet
        KunneIkkeForhåndsvarsle.KunneIkkeGenerereDokument -> feilVedGenereringAvDokument
    }

    fun KunneIkkeLageBrevutkastForRevurdering.tilResultat(): Resultat {
        return when (this) {
            is KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
            KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast -> kunneIkkeLageBrevutkast
            KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson -> fantIkkePerson
            KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> navneoppslagSaksbehandlerAttesttantFeilet
            KunneIkkeLageBrevutkastForRevurdering.KunneIkkeFinneGjeldendeUtbetaling -> fantIkkeGjeldendeUtbetaling
            KunneIkkeLageBrevutkastForRevurdering.DetSkalIkkeSendesBrev -> brevvalgIkkeTillatt
        }
    }

    fun Revurderingsårsak.UgyldigRevurderingsårsak.tilResultat(): Resultat {
        return when (this) {
            Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigBegrunnelse -> BadRequest.errorJson(
                message = "Ugyldig begrunnelse for revurdering",
                code = "revurderingsårsak_ugyldig_begrunnelse",
            )

            Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigÅrsak -> BadRequest.errorJson(
                message = "Ugyldig årsak for revurdering",
                code = "revurderingsårsak_ugyldig_årsak",
            )
        }
    }
}
