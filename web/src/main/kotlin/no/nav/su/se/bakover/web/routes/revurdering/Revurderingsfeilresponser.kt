package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import no.nav.su.se.bakover.common.periode.Periode.UgyldigPeriode
import no.nav.su.se.bakover.service.revurdering.KunneIkkeForhåndsvarsle
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import kotlin.reflect.KClass

internal object Revurderingsfeilresponser {
    val fantIkkeSak = NotFound.errorJson(
        "Fant ikke sak",
        "fant_ikke_sak",
    )
    val fantIkkePerson = NotFound.errorJson(
        "Fant ikke person",
        "fant_ikke_person",
    )
    val fantIkkeAktørId = NotFound.errorJson(
        "Fant ikke aktør id",
        "fant_ikke_aktør_id",
    )
    val kunneIkkeOppretteOppgave = InternalServerError.errorJson(
        "Kunne ikke opprette oppgave",
        "kunne_ikke_opprette_oppgave",
    )
    val fantIkkeRevurdering = NotFound.errorJson(
        "Fant ikke revurdering",
        "fant_ikke_revurdering",
    )
    val manglerBeslutningPåForhåndsvarsel = BadRequest.errorJson(
        "Mangler beslutning på forhåndsvarsel",
        "mangler_beslutning_på_forhåndsvarsel",
    )

    val måVelgeInformasjonSomRevurderes = BadRequest.errorJson(
        "Må velge minst en ting som skal revurderes",
        "må_velge_informasjon_som_revurderes",
    )

    val feilutbetalingStøttesIkke = InternalServerError.errorJson(
        "Feilutbetalinger støttes ikke",
        "feilutbetalinger_støttes_ikke",
    )

    val fantIngenVedtakSomKanRevurderes = NotFound.errorJson(
        "Fant ingen vedtak som kan revurderes for angitt periode",
        "ingenting_å_revurdere_i_perioden",
    )

    val fantIkkeVedtak = NotFound.errorJson(
        "Fant ikke vedtak",
        "fant_ikke_vedtak",
    )

    val fantIkkeTidligereGrunnlagsdata = NotFound.errorJson(
        "Fant ikke grunnlagsdata for tidligere vedtak",
        "fant_ikke_tidligere_grunnlagsdata",
    )

    val tidslinjeForVedtakErIkkeKontinuerlig = InternalServerError.errorJson(
        "Mangler systemstøtte for revurdering av perioder med hull i tidslinjen for vedtak",
        "tidslinje_for_vedtak_ikke_kontinuerlig",
    )

    val bosituasjonMedFlerePerioderMåRevurderes = BadRequest.errorJson(
        "Bosituasjon må revurderes siden det finnes bosituasjonsperioder",
        "bosituasjon_med_flere_perioder_må_revurderes",
    )
    val epsInntektMedFlereBosituasjonsperioderMåRevurderes = BadRequest.errorJson(
        "Inntekt må revurderes siden det finnes EPS inntekt og flere bosituasjonsperioder",
        "eps_inntekt_med_flere_perioder_må_revurderes",
    )
    val epsFormueMedFlereBosituasjonsperioderMåRevurderes = BadRequest.errorJson(
        "Formue må revurderes siden det finnes EPS formue og flere bosituasjonsperioder",
        "eps_formue_med_flere_perioder_må_revurderes",
    )

    val formueListeKanIkkeVæreTom = BadRequest.errorJson(
        "Formueliste kan ikke være tom",
        "formueliste_kan_ikke_være_tom",
    )

    val depositumKanIkkeVæreHøyereEnnInnskudd = BadRequest.errorJson(
        "Depositum kan ikke være høyere enn innskudd",
        "depositum_kan_ikke_være_høyere_enn_innskudd",
    )

    val ikkeLovMedOverlappendePerioder = BadRequest.errorJson(
        "Ikke lov med overlappende perioder",
        "ikke_lov_med_overlappende_perioder",
    )

    val epsFormueperiodeErUtenforBosituasjonPeriode = BadRequest.errorJson(
        "Ikke lov med formueperiode utenfor bosituasjonperioder",
        "ikke_lov_med_formueperiode_utenfor_bosituasjonperiode",
    )

    val formuePeriodeErUtenforBehandlingsperioden = BadRequest.errorJson(
        "Ikke lov med formueperiode utenfor behandlingsperioden",
        "ikke_lov_med_formueperiode_utenfor_behandlingsperioden",
    )

    val måHaEpsHvisManHarSattEpsFormue = BadRequest.errorJson(
        "Ikke lov med formue for eps hvis man ikke har eps",
        "ikke_lov_med_formue_for_eps_hvis_man_ikke_har_eps",
    )

    val formueSomFørerTilOpphørMåRevurderes = BadRequest.errorJson(
        "Formue som fører til opphør må revurderes",
        "formue_som_fører_til_opphør_må_revurderes",
    )

    fun ugyldigPeriode(ugyldigPeriode: UgyldigPeriode): Resultat {
        return BadRequest.errorJson(
            ugyldigPeriode.toString(),
            "ugyldig_periode",
        )
    }

    fun ugyldigTilstand(fra: KClass<*>, til: KClass<*>): Resultat {
        return BadRequest.errorJson(
            "Kan ikke gå fra tilstanden ${fra.simpleName} til tilstanden ${til.simpleName}",
            "ugyldig_tilstand",
        )
    }

    fun KunneIkkeForhåndsvarsle.tilResultat() = when (this) {
        is KunneIkkeForhåndsvarsle.AlleredeForhåndsvarslet -> HttpStatusCode.Conflict.errorJson(
            "Allerede forhåndsvarslet",
            "allerede_forhåndsvarslet",
        )
        is KunneIkkeForhåndsvarsle.FantIkkeAktørId -> fantIkkeAktørId
        is KunneIkkeForhåndsvarsle.FantIkkePerson -> fantIkkePerson
        is KunneIkkeForhåndsvarsle.KunneIkkeDistribuere -> InternalServerError.errorJson(
            "Kunne ikke distribuere brev",
            "kunne_ikke_distribuere_brev",
        )
        is KunneIkkeForhåndsvarsle.KunneIkkeJournalføre -> InternalServerError.errorJson(
            "Kunne ikke journalføre brev",
            "kunne_ikke_journalføre_brev",
        )
        is KunneIkkeForhåndsvarsle.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
        is KunneIkkeForhåndsvarsle.FantIkkeRevurdering -> fantIkkeRevurdering
        is KunneIkkeForhåndsvarsle.UgyldigTilstand -> ugyldigTilstand(this.fra, this.til)
        is KunneIkkeForhåndsvarsle.Attestering -> this.subError.tilResultat()
        is KunneIkkeForhåndsvarsle.KunneIkkeHenteNavnForSaksbehandler -> InternalServerError.errorJson(
            "Kunne ikke hente navn for saksbehandler eller attestant",
            "navneoppslag_feilet",
        )
    }

    fun KunneIkkeLageBrevutkastForRevurdering.tilResultat(): Resultat {
        return when (this) {
            is KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
            KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast -> InternalServerError.errorJson(
                "Kunne ikke lage brevutkast",
                "kunne_ikke_lage_brevutkast",
            )
            KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson -> InternalServerError.errorJson(
                "Fant ikke person",
                "fant_ikke_person",
            )
            KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> InternalServerError.errorJson(
                "Kunne ikke hente navn for saksbehandler eller attestant",
                "navneoppslag_feilet",
            )
            KunneIkkeLageBrevutkastForRevurdering.KunneIkkeFinneGjeldendeUtbetaling -> InternalServerError.errorJson(
                "Kunne ikke hente gjeldende utbetaling",
                "kunne_ikke_hente_gjeldende_utbetaling",
            )
        }
    }
}
