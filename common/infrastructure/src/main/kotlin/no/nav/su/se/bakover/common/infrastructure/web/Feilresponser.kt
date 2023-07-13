package no.nav.su.se.bakover.common.infrastructure.web

import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import kotlin.reflect.KClass

data object Feilresponser {
    val ugyldigTypeSak = BadRequest.errorJson(
        "Ugyldig type sak",
        "ugyldig_type_sak",
    )
    val fantIkkeBehandling = NotFound.errorJson(
        "Fant ikke behandling",
        "fant_ikke_behandling",
    )

    val fantIkkeSak = NotFound.errorJson(
        "Fant ikke sak",
        "fant_ikke_sak",
    )

    val fantIkkeVedtak = NotFound.errorJson(
        "Fant ikke vedtak",
        "fant_ikke_vedtak",
    )

    val ikkeTilgangTilPerson = Forbidden.errorJson(
        "Ikke tilgang til å se person",
        "ikke_tilgang_til_person",
    )

    val fantIkkePerson = NotFound.errorJson(
        "Fant ikke person",
        "fant_ikke_person",
    )

    val feilVedOppslagPåPerson = InternalServerError.errorJson(
        "Feil ved oppslag på person",
        "feil_ved_oppslag_person",
    )

    val fantIkkeSøknad = NotFound.errorJson(
        "Fant ikke søknad",
        "fant_ikke_søknad",
    )

    val fantIkkeKlage = NotFound.errorJson(
        "Fant ikke klage",
        "fant_ikke_klage",
    )

    val harAlleredeÅpenBehandling = BadRequest.errorJson(
        "Har allerede en aktiv behandling",
        "har_allerede_en_aktiv_behandling",
    )

    val vilkårMåVurderesForHeleBehandlingsperioden = BadRequest.errorJson(
        "Vilkår må vurderes for hele behandlingsperioden",
        "vilkår_må_vurderes_for_hele_behandlingsperioden",
    )

    val vilkårKunRelevantForAlder = BadRequest.errorJson(
        "Vilkår er kun relevant for alderssaker!",
        "vilkår_kun_relevant_for_alder",
    )

    val vilkårKunRelevantForUføre = BadRequest.errorJson(
        "Vilkår er kun relevant for uføresaker!",
        "vilkår_kun_relevant_for_uføre",
    )

    val søknadHarBehandlingFraFør = BadRequest.errorJson(
        "Søknad har en behandling fra før",
        "søknad_har_behandling_fra_før",
    )

    val fantIkkeAktørId = NotFound.errorJson(
        "Fant ikke aktør id",
        "fant_ikke_aktør_id",
    )

    val kunneIkkeOppretteOppgave = InternalServerError.errorJson(
        "Kunne ikke opprette oppgave",
        "kunne_ikke_opprette_oppgave",
    )

    val tilbakekrevingsbehandlingErIkkeFullstendig = BadRequest.errorJson(
        message = "Behandling av tilbakekreving er ikke fullstendig og må fullføres først.",
        code = "tilbakekrevingsbehandling_er_ikke_fullstendig",
    )

    val gReguleringKanIkkeFøreTilOpphør = BadRequest.errorJson(
        "G-regulering kan ikke føre til opphør",
        "g_regulering_kan_ikke_føre_til_opphør",
    )

    val brevvalgMangler = BadRequest.errorJson(
        "Brevvalg mangler",
        "brevvalg_mangler",
    )

    val søknadManglerOppgave = InternalServerError.errorJson(
        "Søknad mangler oppgave",
        "søknad_mangler_oppgave",
    )

    val overlappendeVurderingsperioder = BadRequest.errorJson(
        "Vurderingperioder kan ikke overlappe",
        "overlappende_vurderingsperioder",
    )

    val søknadErLukket = BadRequest.errorJson(
        "Søknad er allerede lukket",
        "søknad_er_allerede_lukket",
    )

    val utenforBehandlingsperioden = BadRequest.errorJson(
        "Vurderingsperioden(e) kan ikke være utenfor behandlingsperioden",
        "vurderingsperiode_utenfor_behandlingsperiode",
    )

    val alleVurderingsperioderMåHaSammeResultat = BadRequest.errorJson(
        "Alle vurderingsperiodene må ha samme vurdering (ja/nei)",
        "vurderingene_må_ha_samme_resultat",
    )

    val heleBehandlingsperiodenMåHaVurderinger = BadRequest.errorJson(
        "Hele behandlingsperioden må ha vurderinger",
        "hele_behandlingsperioden_må_ha_vurderinger",
    )

    val alleResultaterMåVæreLike = BadRequest.errorJson(
        "Vurderingsperioden(e) kan ikke inneholde forskjellige resultater",
        "vurderingsperiode_kan_ikke_inneholde_forskjellige_resultater",
    )

    val måVurdereHelePerioden = BadRequest.errorJson(
        "Må vurdere hele perioden",
        "må_vurdere_hele_perioden",
    )

    val måInnheholdeKunEnVurderingsperiode = BadRequest.errorJson(
        "Må innheholde kun en vurderingsperiode",
        "må_inneholde_kun_en_vurderingsperiode",
    )

    val ugyldigBody = BadRequest.errorJson(
        "Ugyldig body",
        "ugyldig_body",
    )

    val ugyldigInput = BadRequest.errorJson(
        "Ugyldig input",
        "ugyldig_input",
    )

    val ugyldigFødselsnummer = BadRequest.errorJson(
        "Ugyldig fødselsnummer",
        "ugyldig_fødselsnummer",
    )

    val feilVedGenereringAvDokument = InternalServerError.errorJson(
        "Feil ved generering av dokument",
        "feil_ved_generering_av_dokument",
    )

    val behandlingErIUgyldigTilstand = InternalServerError.errorJson(
        "Behandlingen er i ugyldig tilstand for avslag",
        "behandling_i_ugyldig_tilstand_for_avslag",
    )

    val fantIkkeSaksbehandlerEllerAttestant = NotFound.errorJson(
        "Fant ikke saksbehandler eller attestant",
        "fant_ikke_saksbehandler_eller_attestant",
    )

    val datoMåVæreFørsteIMåned = BadRequest.errorJson(
        "Dato må være første dag i måneden.",
        "dato_må_være_første_i_mnd",
    )

    val kunneIkkeHenteNesteKontrollsamtale = InternalServerError.errorJson(
        "Kunne ikke hente neste kontrollsamtale",
        "kunne_ikke_hente_neste_kontrollsamtale",
    )

    val fantIkkeGjeldendeStønadsperiode = NotFound.errorJson(
        "Fant ikke gjeldende stønadsperiode",
        "fant_ikke_gjeldende_stønadsperiode",
    )

    val ugyldigStatusovergangKontrollsamtale = NotFound.errorJson(
        "Kontrollsamtalen som forsøkes å endre er i feil tilstand",
        "ugyldig_statusovergang_kontrollsamtale",
    )

    val fantIkkeGjeldendeUtbetaling = NotFound.errorJson(
        "Fant ikke gjeldende utbetaling",
        "fant_ikke_gjeldende_utbetaling",
    )

    val attestantOgSaksbehandlerKanIkkeVæreSammePerson = Forbidden.errorJson(
        "Attestant og saksbehandler kan ikke være samme person",
        "attestant_og_saksbehandler_kan_ikke_være_samme_person",
    )

    val feilVedHentingAvSaksbehandlerNavn = InternalServerError.errorJson(
        "Feil ved henting av saksbehandler navn",
        "feil_ved_henting_av_saksbehandler_navn",
    )
    val feilVedHentingAvAttestantNavn = InternalServerError.errorJson(
        "Feil ved henting av attestant navn",
        "feil_ved_henting_av_attestant_navn",
    )

    val feilVedHentingAvVedtakDato = InternalServerError.errorJson(
        "Feil ved henting av vedtak dato",
        "feil_ved_henting_av_vedtak_dato",
    )

    val kunneIkkeSimulere = InternalServerError.errorJson(
        "Kunne ikke simulere",
        "kunne_ikke_simulere",
    )

    val avkortingErUfullstendig = InternalServerError.errorJson(
        "Hele det utestående beløpet som skal avkortes pga. utenlandsopphold kunne ikke trekkes fra i valgt stønadsperiode. Det er ikke støtte for å overføre restbeløp til neste stønadsperiode",
        "avkorting_er_ufullstendig",
    )

    val avkortingsfeil = InternalServerError.errorJson(
        "Kunne ikke iverksette pga. avkorting. Dersom dette er en revurdering, prøv å oppdater revurderingen for å hente nye data fra saken.",
        "avkortingsfeil",
    )

    val opphørAvYtelseSomSkalAvkortes = InternalServerError.errorJson(
        "Opphør av ytelse som skal avkortes støttes ikke.",
        "opphør_av_ytelse_som_skal_avkortes",
    )

    val lagringFeilet = InternalServerError.errorJson(
        "Kunne ikke lagre",
        "kunne_ikke_lagre",
    )

    val kryssjekkTidslinjeSimuleringFeilet = InternalServerError.errorJson(
        "Kryssjekk av utbetalingstidslinjer og simulering feilet",
        "kryssjekk_utbetalingstidslinjer_simulering_feilet",
    )

    val skalIkkeSendesBrev = InternalServerError.errorJson(
        "Skal ikke sendes brev",
        "skal_ikke_sendes_brev",
    )

    val ugyldigMåned = BadRequest.errorJson(
        "Ugyldig måned",
        "ugyldig_måned",
    )

    val ugyldigDato = BadRequest.errorJson(
        "Ugyldig dato",
        "ugyldig_dato",
    )

    val ukjentFeil = InternalServerError.errorJson(
        "Ukjent feil",
        "ukjent_feil",
    )

    val ugyldigTilstand = BadRequest.errorJson("Ugyldig tilstand", "ugyldig_tilstand")

    fun ugyldigTilstand(fra: KClass<*>, til: KClass<*>): Resultat {
        return ugyldigTilstand(fra.simpleName.toString(), til.simpleName.toString())
    }

    fun ugyldigTilstand(fra: String, til: String): Resultat {
        return BadRequest.errorJson(
            "Kan ikke gå fra tilstanden $fra til tilstanden $til",
            "ugyldig_tilstand",
        )
    }

    data object Uføre {
        val uføregradMåVæreMellomEnOgHundre = BadRequest.errorJson(
            message = "Uføregrad må være mellom en og hundre",
            code = "uføregrad_må_være_mellom_en_og_hundre",
        )
        val uføregradOgForventetInntektMangler = BadRequest.errorJson(
            "Hvis man innvilger uførevilkåret må man sende med uføregrad og forventet inntekt",
            "uføregrad_og_forventet_inntekt_mangler",
        )
        val periodeForGrunnlagOgVurderingErForskjellig = BadRequest.errorJson(
            "Det er ikke samsvar mellom perioden for vurdering og perioden for grunnlaget",
            "periode_for_grunnlag_og_vurdering_er_forskjellig",
        )
    }

    val depositumErHøyereEnnInnskudd = BadRequest.errorJson(
        "Depositum er høyere enn innskudd",
        "depositum_høyere_enn_innskudd",
    )

    val periodeForGrunnlagOgVurderingErForskjellig = BadRequest.errorJson(
        "Det er ikke samsvar mellom perioden for vurdering og perioden for grunnlaget",
        "periode_for_grunnlag_og_vurdering_er_forskjellig",
    )

    val kunneIkkeLeggeTilFradragsgrunnlag = BadRequest.errorJson(
        "Kunne ikke legge til fradragsgrunnlag",
        "kunne_ikke_legge_til_fradragsgrunnlag",
    )

    val harIkkeEktefelle = BadRequest.errorJson(
        "Kan ikke ha formue for eps når søker ikke har eps",
        "har_ikke_ektefelle",
    )

    val sakAvventerKravgrunnlagForTilbakekreving = BadRequest.errorJson(
        message = "Saken avventer kravgrunnlag for tilbakekreving. Nye utbetalinger kan ikke håndteres før kravgrunnlaget er ferdigbehandlet.",
        code = "åpent_kravgrunnlag_må_håndteres_før_ny_søknadsbehandling",
    )

    val detHarKommetNyeOverlappendeVedtak = BadRequest.errorJson(
        message = "Det har kommet nye vedtak i denne revurderingsperioden etter at denne revurderingen ble opprettet eller oppdatert.",
        code = "nye_overlappende_vedtak",
    )

    data object Brev {
        val kunneIkkeGenerereBrev = InternalServerError.errorJson(
            "Kunne ikke generere brev",
            "kunne_ikke_generere_brev",
        )
        val kunneIkkeLageBrevutkast = InternalServerError.errorJson(
            "Kunne ikke lage brevutkast",
            "kunne_ikke_lage_brevutkast",
        )
        val kanIkkeSendeBrevIDenneTilstanden = InternalServerError.errorJson(
            "Kan ikke sende brev i denne tilstanden",
            "kan_ikke_sende_brev_i_denne_tilstanden",
        )
    }

    val inneholderUfullstendigeBosituasjoner = BadRequest.errorJson(
        "Behandlingen inneholder ufullstendige bosituasjoner",
        "inneholder_ufullstendige_bosituasjoner",
    )
}
