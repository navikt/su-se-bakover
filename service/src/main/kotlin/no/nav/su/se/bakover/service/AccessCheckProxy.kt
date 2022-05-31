package no.nav.su.se.bakover.service

import arrow.core.Either
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.BegrensetSakinfo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.SendPåminnelseNyStønadsperiodeContext
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeAvslutteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.KunneIkkeIverksetteAvvistKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLeggeTilFritekstForAvvist
import no.nav.su.se.bakover.domain.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeTolkeKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.KunneIkkeUnderkjenne
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.UprosessertKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtale
import no.nav.su.se.bakover.domain.nøkkeltall.Nøkkeltall
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Kravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonRepo
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringMerknad
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeAvslutteRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.sak.Behandlingsoversikt
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeIverksette
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import no.nav.su.se.bakover.service.avstemming.AvstemmingFeilet
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.HentDokumenterForIdType
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.service.klage.KlageVurderingerRequest
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseService
import no.nav.su.se.bakover.service.klage.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.service.klage.NyKlageRequest
import no.nav.su.se.bakover.service.klage.UnderkjennKlageRequest
import no.nav.su.se.bakover.service.klage.VurderKlagevilkårRequest
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.kontrollsamtale.KunneIkkeHenteKontrollsamtale
import no.nav.su.se.bakover.service.kontrollsamtale.KunneIkkeSetteNyDatoForKontrollsamtale
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.regulering.KunneIkkeAvslutte
import no.nav.su.se.bakover.service.regulering.KunneIkkeOppretteRegulering
import no.nav.su.se.bakover.service.regulering.KunneIkkeRegulereManuelt
import no.nav.su.se.bakover.service.regulering.ReguleringService
import no.nav.su.se.bakover.service.revurdering.Forhåndsvarselhandling
import no.nav.su.se.bakover.service.revurdering.FortsettEtterForhåndsvarselFeil
import no.nav.su.se.bakover.service.revurdering.FortsettEtterForhåndsvarslingRequest
import no.nav.su.se.bakover.service.revurdering.GjenopptaYtelseRequest
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeForhåndsvarsle
import no.nav.su.se.bakover.service.revurdering.KunneIkkeGjenopptaYtelse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteGjenopptakAvYtelse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteStansYtelse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLageBrevutkastForAvsluttingAvRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilBosituasjongrunnlag
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilOpplysningsplikt
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilUføreVilkår
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilUtenlandsopphold
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppdatereTilbakekrevingsbehandling
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeSendeRevurderingTilAttestering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeStanseYtelse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeUnderkjenneRevurdering
import no.nav.su.se.bakover.service.revurdering.LeggTilBosituasjonerRequest
import no.nav.su.se.bakover.service.revurdering.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.service.revurdering.OppdaterRevurderingRequest
import no.nav.su.se.bakover.service.revurdering.OppdaterTilbakekrevingsbehandlingRequest
import no.nav.su.se.bakover.service.revurdering.OpprettRevurderingRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingOgFeilmeldingerResponse
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.revurdering.SendTilAttesteringRequest
import no.nav.su.se.bakover.service.revurdering.StansYtelseRequest
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.KunneIkkeHenteGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Statistikk
import no.nav.su.se.bakover.service.statistikk.StatistikkService
import no.nav.su.se.bakover.service.søknad.AvslåManglendeDokumentasjonRequest
import no.nav.su.se.bakover.service.søknad.AvslåSøknadManglendeDokumentasjonService
import no.nav.su.se.bakover.service.søknad.FantIkkeSøknad
import no.nav.su.se.bakover.service.søknad.KunneIkkeAvslåSøknad
import no.nav.su.se.bakover.service.søknad.KunneIkkeLageSøknadPdf
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteSøknad
import no.nav.su.se.bakover.service.søknad.OpprettManglendeJournalpostOgOppgaveResultat
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknadsbehandling.VilkårsvurderRequest
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.utbetaling.FantIkkeUtbetaling
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFlereUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUtenlandsoppholdRequest
import java.time.LocalDate
import java.util.UUID

open class AccessCheckProxy(
    private val personRepo: PersonRepo,
    private val services: Services,
) {
    fun proxy(): Services {
        return Services(
            avstemming = object : AvstemmingService {
                override fun grensesnittsavstemming(): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
                    return services.avstemming.grensesnittsavstemming()
                }

                override fun grensesnittsavstemming(
                    fraOgMed: Tidspunkt,
                    tilOgMed: Tidspunkt,
                ): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
                    return services.avstemming.grensesnittsavstemming(fraOgMed, tilOgMed)
                }

                override fun konsistensavstemming(
                    løpendeFraOgMed: LocalDate,
                ): Either<AvstemmingFeilet, Avstemming.Konsistensavstemming.Ny> {
                    return services.avstemming.konsistensavstemming(løpendeFraOgMed)
                }

                override fun konsistensavstemmingUtførtForOgPåDato(dato: LocalDate): Boolean {
                    return services.avstemming.konsistensavstemmingUtførtForOgPåDato(dato)
                }
            },
            utbetaling = object : UtbetalingService {
                override fun hentUtbetaling(utbetalingId: UUID30): Either<FantIkkeUtbetaling, Utbetaling> {
                    assertHarTilgangTilUtbetaling(utbetalingId)
                    return services.utbetaling.hentUtbetaling(utbetalingId)
                }

                override fun hentUtbetalingerForSakId(sakId: UUID) = kastKanKunKallesFraAnnenService()

                override fun oppdaterMedKvittering(
                    avstemmingsnøkkel: Avstemmingsnøkkel,
                    kvittering: Kvittering,
                ) = kastKanKunKallesFraAnnenService()

                override fun simulerUtbetaling(
                    request: SimulerUtbetalingRequest.NyUtbetalingRequest,
                ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
                    assertHarTilgangTilSak(request.sakId)

                    return services.utbetaling.simulerUtbetaling(request)
                }

                override fun simulerOpphør(
                    request: SimulerUtbetalingRequest.OpphørRequest,
                ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
                    assertHarTilgangTilSak(request.sakId)

                    return services.utbetaling.simulerOpphør(request)
                }

                override fun publiserUtbetaling(
                    utbetaling: Utbetaling.SimulertUtbetaling,
                ) = kastKanKunKallesFraAnnenService()

                override fun lagreUtbetaling(
                    utbetaling: Utbetaling.SimulertUtbetaling,
                    transactionContext: TransactionContext?,
                ) = kastKanKunKallesFraAnnenService()

                override fun verifiserOgSimulerUtbetaling(
                    request: UtbetalRequest.NyUtbetaling,
                ) = kastKanKunKallesFraAnnenService()

                override fun simulerStans(
                    request: SimulerUtbetalingRequest.StansRequest,
                ) = kastKanKunKallesFraAnnenService()

                override fun stansUtbetalinger(
                    request: UtbetalRequest.Stans,
                ) = kastKanKunKallesFraAnnenService()

                override fun simulerGjenopptak(
                    request: SimulerUtbetalingRequest.GjenopptakRequest,
                ) = kastKanKunKallesFraAnnenService()

                override fun gjenopptaUtbetalinger(
                    request: UtbetalRequest.Gjenopptak,
                ) = kastKanKunKallesFraAnnenService()

                override fun verifiserOgSimulerOpphør(
                    request: UtbetalRequest.Opphør,
                ) = kastKanKunKallesFraAnnenService()

                override fun hentGjeldendeUtbetaling(
                    sakId: UUID,
                    forDato: LocalDate,
                ) = kastKanKunKallesFraAnnenService()
            },
            sak = object : SakService {
                override fun hentSak(sakId: UUID): Either<FantIkkeSak, Sak> {
                    assertHarTilgangTilSak(sakId)

                    return services.sak.hentSak(sakId)
                }

                override fun hentSak(fnr: Fnr): Either<FantIkkeSak, Sak> {
                    // Siden vi også vil kontrollere på EPS må vi hente ut saken først
                    // og sjekke på hele den (i stedet for å gjøre assertHarTilgangTilPerson(fnr))
                    return services.sak.hentSak(fnr)
                        .also {
                            it.map { sak -> assertHarTilgangTilSak(sak.id) }
                        }
                }

                override fun hentSak(saksnummer: Saksnummer): Either<FantIkkeSak, Sak> {
                    return services.sak.hentSak(saksnummer)
                        .also {
                            it.map { sak -> assertHarTilgangTilSak(sak.id) }
                        }
                }

                override fun hentGjeldendeVedtaksdata(
                    sakId: UUID,
                    periode: Periode,
                ): Either<KunneIkkeHenteGjeldendeVedtaksdata, GjeldendeVedtaksdata?> {
                    assertHarTilgangTilSak(sakId)
                    return services.sak.hentGjeldendeVedtaksdata(sakId, periode)
                }

                override fun hentSakidOgSaksnummer(fnr: Fnr) = kastKanKunKallesFraAnnenService()

                override fun opprettSak(sak: NySak) {
                    assertHarTilgangTilPerson(sak.fnr)

                    return services.sak.opprettSak(sak)
                }

                override fun hentÅpneBehandlingerForAlleSaker(): List<Behandlingsoversikt> {
                    // vi gjør ikke noe assert fordi vi ikke sender noe sensitiv info.
                    // Samtidig som at dem går gjennom hentSak() når de skal saksbehandle
                    return services.sak.hentÅpneBehandlingerForAlleSaker()
                }

                override fun hentFerdigeBehandlingerForAlleSaker(): List<Behandlingsoversikt> {
                    return services.sak.hentFerdigeBehandlingerForAlleSaker()
                }

                override fun hentBegrensetSakinfo(fnr: Fnr): Either<FantIkkeSak, BegrensetSakinfo> {
                    assertHarTilgangTilPerson(fnr)
                    return services.sak.hentBegrensetSakinfo(fnr)
                }
            },
            søknad = object : SøknadService {
                override fun nySøknad(søknadInnhold: SøknadInnhold, identBruker: NavIdentBruker): Either<KunneIkkeOppretteSøknad, Pair<Saksnummer, Søknad>> {
                    assertHarTilgangTilPerson(søknadInnhold.personopplysninger.fnr)

                    return services.søknad.nySøknad(søknadInnhold, identBruker)
                }

                override fun lukkSøknad(
                    søknad: Søknad.Journalført.MedOppgave.Lukket,
                    sessionContext: SessionContext,
                ) = kastKanKunKallesFraAnnenService()

                override fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad> {
                    assertHarTilgangTilSøknad(søknadId)

                    return services.søknad.hentSøknad(søknadId)
                }

                override fun hentSøknadPdf(søknadId: UUID): Either<KunneIkkeLageSøknadPdf, ByteArray> {
                    assertHarTilgangTilSøknad(søknadId)

                    return services.søknad.hentSøknadPdf(søknadId)
                }

                override fun opprettManglendeJournalpostOgOppgave(): OpprettManglendeJournalpostOgOppgaveResultat {
                    // Dette er et driftsendepunkt og vi vil ikke returnere kode 6/7/person-sensitive data.

                    return services.søknad.opprettManglendeJournalpostOgOppgave()
                }
            },
            brev = object : BrevService {
                override fun lagBrev(request: LagBrevRequest) = kastKanKunKallesFraAnnenService()

                override fun lagDokument(request: LagBrevRequest): Either<KunneIkkeLageDokument, Dokument.UtenMetadata> {
                    kastKanKunKallesFraAnnenService()
                }

                override fun journalførOgDistribuerUtgåendeDokumenter() = kastKanKunKallesFraAnnenService()

                override fun lagreDokument(dokument: Dokument.MedMetadata) = kastKanKunKallesFraAnnenService()

                override fun lagreDokument(
                    dokument: Dokument.MedMetadata,
                    transactionContext: TransactionContext,
                ) = kastKanKunKallesFraAnnenService()

                override fun hentDokumenterFor(hentDokumenterForIdType: HentDokumenterForIdType): List<Dokument> {
                    when (hentDokumenterForIdType) {
                        is HentDokumenterForIdType.Revurdering -> assertHarTilgangTilRevurdering(hentDokumenterForIdType.id)
                        is HentDokumenterForIdType.Sak -> assertHarTilgangTilSak(hentDokumenterForIdType.id)
                        is HentDokumenterForIdType.Søknad -> assertHarTilgangTilSøknad(hentDokumenterForIdType.id)
                        is HentDokumenterForIdType.Vedtak -> assertHarTilgangTilVedtak(hentDokumenterForIdType.id)
                    }.let {
                        return services.brev.hentDokumenterFor(hentDokumenterForIdType)
                    }
                }

                override fun lagDokument(
                    visitable: Visitable<LagBrevRequestVisitor>,
                ) = kastKanKunKallesFraAnnenService()

                override fun lagBrevRequest(
                    visitable: Visitable<LagBrevRequestVisitor>,
                ) = kastKanKunKallesFraAnnenService()
            },
            lukkSøknad = object : LukkSøknadService {
                override fun lukkSøknad(request: LukkSøknadRequest): Either<KunneIkkeLukkeSøknad, Sak> {
                    assertHarTilgangTilSøknad(request.søknadId)

                    return services.lukkSøknad.lukkSøknad(request)
                }

                override fun lagBrevutkast(
                    request: LukkSøknadRequest,
                ): Either<no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLageBrevutkast, ByteArray> {
                    assertHarTilgangTilSøknad(request.søknadId)

                    return services.lukkSøknad.lagBrevutkast(request)
                }
            },
            oppgave = object : OppgaveService {
                override fun opprettOppgave(config: OppgaveConfig) = kastKanKunKallesFraAnnenService()
                override fun opprettOppgaveMedSystembruker(config: OppgaveConfig) = kastKanKunKallesFraAnnenService()
                override fun lukkOppgave(oppgaveId: OppgaveId) = kastKanKunKallesFraAnnenService()
                override fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId) = kastKanKunKallesFraAnnenService()
                override fun oppdaterOppgave(
                    oppgaveId: OppgaveId,
                    beskrivelse: String,
                ) = kastKanKunKallesFraAnnenService()
            },
            person = object : PersonService {
                override fun hentPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Person> {
                    assertHarTilgangTilPerson(fnr)
                    return services.person.hentPerson(fnr)
                }

                override fun hentPersonMedSystembruker(fnr: Fnr) = kastKanKunKallesFraAnnenService()

                override fun hentAktørId(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
                    assertHarTilgangTilPerson(fnr)
                    return services.person.hentAktørId(fnr)
                }

                override fun hentAktørIdMedSystembruker(fnr: Fnr) = kastKanKunKallesFraAnnenService()

                override fun sjekkTilgangTilPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Unit> {
                    return services.person.sjekkTilgangTilPerson(fnr)
                }
            },
            statistikk = object : StatistikkService {
                override fun publiser(statistikk: Statistikk) = kastKanKunKallesFraAnnenService()
            },
            toggles = services.toggles,
            søknadsbehandling = object : SøknadsbehandlingService {
                override fun opprett(request: SøknadsbehandlingService.OpprettRequest): Either<SøknadsbehandlingService.KunneIkkeOpprette, Søknadsbehandling.Vilkårsvurdert.Uavklart> {
                    assertHarTilgangTilSøknad(request.søknadId)
                    return services.søknadsbehandling.opprett(request)
                }

                override fun vilkårsvurder(request: VilkårsvurderRequest): Either<SøknadsbehandlingService.KunneIkkeVilkårsvurdere, Søknadsbehandling.Vilkårsvurdert> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.vilkårsvurder(request)
                }

                override fun beregn(request: SøknadsbehandlingService.BeregnRequest): Either<SøknadsbehandlingService.KunneIkkeBeregne, Søknadsbehandling.Beregnet> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.beregn(request)
                }

                override fun simuler(request: SøknadsbehandlingService.SimulerRequest): Either<SøknadsbehandlingService.KunneIkkeSimulereBehandling, Søknadsbehandling.Simulert> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.simuler(request)
                }

                override fun sendTilAttestering(request: SøknadsbehandlingService.SendTilAttesteringRequest): Either<SøknadsbehandlingService.KunneIkkeSendeTilAttestering, Søknadsbehandling.TilAttestering> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.sendTilAttestering(request)
                }

                override fun underkjenn(request: SøknadsbehandlingService.UnderkjennRequest): Either<SøknadsbehandlingService.KunneIkkeUnderkjenne, Søknadsbehandling.Underkjent> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.underkjenn(request)
                }

                override fun iverksett(request: SøknadsbehandlingService.IverksettRequest): Either<KunneIkkeIverksette, Søknadsbehandling.Iverksatt> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.iverksett(request)
                }

                override fun brev(request: SøknadsbehandlingService.BrevRequest): Either<SøknadsbehandlingService.KunneIkkeLageBrev, ByteArray> {
                    assertHarTilgangTilBehandling(request.behandling.id)
                    return services.søknadsbehandling.brev(request)
                }

                override fun hent(request: SøknadsbehandlingService.HentRequest): Either<SøknadsbehandlingService.FantIkkeBehandling, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.hent(request)
                }

                override fun oppdaterStønadsperiode(request: SøknadsbehandlingService.OppdaterStønadsperiodeRequest): Either<SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.oppdaterStønadsperiode(request)
                }

                override fun leggTilUførevilkår(request: LeggTilUførevurderingerRequest): Either<SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.leggTilUførevilkår(request)
                }

                override fun leggTilBosituasjonEpsgrunnlag(request: LeggTilBosituasjonEpsRequest): Either<SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.leggTilBosituasjonEpsgrunnlag(request)
                }

                override fun fullførBosituasjongrunnlag(request: FullførBosituasjonRequest): Either<SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.fullførBosituasjongrunnlag(request)
                }

                override fun leggTilFradragsgrunnlag(request: LeggTilFradragsgrunnlagRequest): Either<SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.leggTilFradragsgrunnlag(request)
                }

                override fun leggTilFormuevilkår(request: LeggTilFormuevilkårRequest): Either<SøknadsbehandlingService.KunneIkkeLeggeTilFormuegrunnlag, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.leggTilFormuevilkår(request)
                }

                override fun hentForSøknad(søknadId: UUID) = kastKanKunKallesFraAnnenService()

                override fun lukk(
                    lukketSøknadbehandling: LukketSøknadsbehandling,
                    tx: TransactionContext,
                ) = kastKanKunKallesFraAnnenService()

                override fun lagre(
                    avslag: AvslagManglendeDokumentasjon,
                    tx: TransactionContext,
                ) = kastKanKunKallesFraAnnenService()

                override fun leggTilUtenlandsopphold(request: LeggTilUtenlandsoppholdRequest): Either<SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold, Søknadsbehandling.Vilkårsvurdert> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.leggTilUtenlandsopphold(request)
                }

                override fun leggTilOpplysningspliktVilkår(request: LeggTilOpplysningspliktRequest.Søknadsbehandling): Either<KunneIkkeLeggeTilOpplysningsplikt, Søknadsbehandling.Vilkårsvurdert> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.leggTilOpplysningspliktVilkår(request)
                }
            },
            ferdigstillVedtak = object : FerdigstillVedtakService {
                override fun ferdigstillVedtakEtterUtbetaling(
                    utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering,
                ) = kastKanKunKallesFraAnnenService()

                override fun lukkOppgaveMedBruker(behandling: Behandling) = kastKanKunKallesFraAnnenService()
            },
            revurdering = object : RevurderingService {
                override fun hentRevurdering(revurderingId: UUID): AbstraktRevurdering? {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.hentRevurdering(revurderingId)
                }

                override fun stansAvYtelse(request: StansYtelseRequest): Either<KunneIkkeStanseYtelse, StansAvYtelseRevurdering.SimulertStansAvYtelse> {
                    assertHarTilgangTilSak(request.sakId)
                    return services.revurdering.stansAvYtelse(request)
                }

                override fun iverksettStansAvYtelse(
                    revurderingId: UUID,
                    attestant: NavIdentBruker.Attestant,
                ): Either<KunneIkkeIverksetteStansYtelse, StansAvYtelseRevurdering.IverksattStansAvYtelse> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.iverksettStansAvYtelse(revurderingId, attestant)
                }

                override fun gjenopptaYtelse(request: GjenopptaYtelseRequest): Either<KunneIkkeGjenopptaYtelse, GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse> {
                    assertHarTilgangTilSak(request.sakId)
                    return services.revurdering.gjenopptaYtelse(request)
                }

                override fun iverksettGjenopptakAvYtelse(
                    revurderingId: UUID,
                    attestant: NavIdentBruker.Attestant,
                ): Either<KunneIkkeIverksetteGjenopptakAvYtelse, GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.iverksettGjenopptakAvYtelse(revurderingId, attestant)
                }

                override fun opprettRevurdering(
                    opprettRevurderingRequest: OpprettRevurderingRequest,
                ): Either<KunneIkkeOppretteRevurdering, OpprettetRevurdering> {
                    assertHarTilgangTilSak(opprettRevurderingRequest.sakId)
                    return services.revurdering.opprettRevurdering(opprettRevurderingRequest)
                }

                override fun oppdaterRevurdering(
                    oppdaterRevurderingRequest: OppdaterRevurderingRequest,
                ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
                    assertHarTilgangTilRevurdering(oppdaterRevurderingRequest.revurderingId)
                    return services.revurdering.oppdaterRevurdering(oppdaterRevurderingRequest)
                }

                override fun beregnOgSimuler(
                    revurderingId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                ): Either<KunneIkkeBeregneOgSimulereRevurdering, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.beregnOgSimuler(
                        revurderingId = revurderingId,
                        saksbehandler = saksbehandler,
                    )
                }

                override fun lagreOgSendForhåndsvarsel(
                    revurderingId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                    forhåndsvarselhandling: Forhåndsvarselhandling,
                    fritekst: String,
                ): Either<KunneIkkeForhåndsvarsle, Revurdering> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.lagreOgSendForhåndsvarsel(
                        revurderingId,
                        saksbehandler,
                        forhåndsvarselhandling,
                        fritekst,
                    )
                }

                override fun lagBrevutkastForForhåndsvarsling(
                    revurderingId: UUID,
                    fritekst: String,
                ): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.lagBrevutkastForForhåndsvarsling(revurderingId, fritekst)
                }

                override fun sendTilAttestering(
                    request: SendTilAttesteringRequest,
                ): Either<KunneIkkeSendeRevurderingTilAttestering, Revurdering> {
                    assertHarTilgangTilRevurdering(request.revurderingId)
                    return services.revurdering.sendTilAttestering(request)
                }

                override fun oppdaterTilbakekrevingsbehandling(request: OppdaterTilbakekrevingsbehandlingRequest): Either<KunneIkkeOppdatereTilbakekrevingsbehandling, SimulertRevurdering> {
                    assertHarTilgangTilRevurdering(request.revurderingId)
                    return services.revurdering.oppdaterTilbakekrevingsbehandling(request)
                }

                override fun lagBrevutkastForRevurdering(
                    revurderingId: UUID,
                    fritekst: String?,
                ): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.lagBrevutkastForRevurdering(revurderingId, fritekst)
                }

                override fun iverksett(
                    revurderingId: UUID,
                    attestant: NavIdentBruker.Attestant,
                ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.iverksett(revurderingId, attestant)
                }

                override fun underkjenn(
                    revurderingId: UUID,
                    attestering: Attestering.Underkjent,
                ): Either<KunneIkkeUnderkjenneRevurdering, UnderkjentRevurdering> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.underkjenn(revurderingId, attestering)
                }

                override fun fortsettEtterForhåndsvarsling(request: FortsettEtterForhåndsvarslingRequest): Either<FortsettEtterForhåndsvarselFeil, Revurdering> {
                    assertHarTilgangTilRevurdering(request.revurderingId)
                    return services.revurdering.fortsettEtterForhåndsvarsling(request)
                }

                override fun leggTilUførevilkår(
                    request: LeggTilUførevurderingerRequest,
                ): Either<KunneIkkeLeggeTilUføreVilkår, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId)
                    return services.revurdering.leggTilUførevilkår(request)
                }

                override fun leggTilUtenlandsopphold(
                    request: LeggTilFlereUtenlandsoppholdRequest,
                ): Either<KunneIkkeLeggeTilUtenlandsopphold, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId)
                    return services.revurdering.leggTilUtenlandsopphold(request)
                }

                override fun leggTilFradragsgrunnlag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradragsgrunnlag, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId)
                    return services.revurdering.leggTilFradragsgrunnlag(request)
                }

                override fun leggTilBosituasjongrunnlag(request: LeggTilBosituasjonerRequest): Either<KunneIkkeLeggeTilBosituasjongrunnlag, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.revurderingId)
                    return services.revurdering.leggTilBosituasjongrunnlag(request)
                }

                override fun leggTilFormuegrunnlag(request: LeggTilFormuevilkårRequest): Either<KunneIkkeLeggeTilFormuegrunnlag, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId)
                    return services.revurdering.leggTilFormuegrunnlag(request)
                }

                override fun avsluttRevurdering(
                    revurderingId: UUID,
                    begrunnelse: String,
                    fritekst: String?,
                ): Either<KunneIkkeAvslutteRevurdering, AbstraktRevurdering> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.avsluttRevurdering(revurderingId, begrunnelse, fritekst)
                }

                override fun leggTilOpplysningspliktVilkår(request: LeggTilOpplysningspliktRequest.Revurdering): Either<KunneIkkeLeggeTilOpplysningsplikt, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId)
                    return services.revurdering.leggTilOpplysningspliktVilkår(request)
                }

                override fun lagBrevutkastForAvslutting(
                    revurderingId: UUID,
                    fritekst: String?,
                ): Either<KunneIkkeLageBrevutkastForAvsluttingAvRevurdering, Pair<Fnr, ByteArray>> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.lagBrevutkastForAvslutting(revurderingId, fritekst)
                }
            },
            vedtakService = object : VedtakService {
                override fun lagre(vedtak: Vedtak) = kastKanKunKallesFraAnnenService()

                override fun lagre(
                    vedtak: Vedtak,
                    sessionContext: TransactionContext,
                ) = kastKanKunKallesFraAnnenService()

                override fun hentForVedtakId(vedtakId: UUID) = kastKanKunKallesFraAnnenService()

                override fun hentForRevurderingId(revurderingId: UUID) = kastKanKunKallesFraAnnenService()

                override fun hentJournalpostId(vedtakId: UUID) = kastKanKunKallesFraAnnenService()

                override fun hentAktiveFnr(fomDato: LocalDate): List<Fnr> {
                    return services.vedtakService.hentAktiveFnr(fomDato)
                }

                override fun kopierGjeldendeVedtaksdata(
                    sakId: UUID,
                    fraOgMed: LocalDate,
                ) = kastKanKunKallesFraAnnenService()

                override fun hentForUtbetaling(utbetalingId: UUID30) = kastKanKunKallesFraAnnenService()

                override fun historiskGrunnlagForVedtaksperiode(
                    sakId: UUID,
                    vedtakId: UUID,
                ): Either<KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak, GjeldendeVedtaksdata> {
                    return services.vedtakService.historiskGrunnlagForVedtaksperiode(sakId, vedtakId)
                }
            },
            nøkkeltallService = object : NøkkeltallService {
                override fun hentNøkkeltall(): Nøkkeltall {
                    return services.nøkkeltallService.hentNøkkeltall()
                }
            },
            avslåSøknadManglendeDokumentasjonService = object : AvslåSøknadManglendeDokumentasjonService {
                override fun avslå(request: AvslåManglendeDokumentasjonRequest): Either<KunneIkkeAvslåSøknad, Sak> {
                    assertHarTilgangTilSøknad(request.søknadId)
                    return services.avslåSøknadManglendeDokumentasjonService.avslå(request)
                }
            },
            kontrollsamtale = object : KontrollsamtaleService {
                override fun nyDato(
                    sakId: UUID,
                    dato: LocalDate,
                ): Either<KunneIkkeSetteNyDatoForKontrollsamtale, Unit> {
                    assertHarTilgangTilSak(sakId)
                    return services.kontrollsamtale.nyDato(sakId, dato)
                }

                override fun hentNestePlanlagteKontrollsamtale(
                    sakId: UUID,
                    sessionContext: SessionContext,
                ): Either<KunneIkkeHenteKontrollsamtale, Kontrollsamtale> {
                    assertHarTilgangTilSak(sakId)
                    return services.kontrollsamtale.hentNestePlanlagteKontrollsamtale(sakId)
                }

                override fun kallInn(
                    sakId: UUID,
                    kontrollsamtale: Kontrollsamtale,
                ) = kastKanKunKallesFraAnnenService()

                override fun hentPlanlagteKontrollsamtaler(
                    sessionContext: SessionContext,
                ) = kastKanKunKallesFraAnnenService()

                override fun opprettPlanlagtKontrollsamtale(
                    vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling,
                    sessionContext: SessionContext,
                ) = kastKanKunKallesFraAnnenService()

                override fun annullerKontrollsamtale(
                    sakId: UUID,
                    sessionContext: SessionContext,
                ) = kastKanKunKallesFraAnnenService()

                override fun defaultSessionContext() = services.kontrollsamtale.defaultSessionContext()
            },
            klageService = object : KlageService {
                override fun opprett(request: NyKlageRequest): Either<KunneIkkeOppretteKlage, OpprettetKlage> {
                    assertHarTilgangTilSak(request.sakId)
                    return services.klageService.opprett(request)
                }

                override fun vilkårsvurder(request: VurderKlagevilkårRequest): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
                    assertHarTilgangTilKlage(request.klageId)
                    return services.klageService.vilkårsvurder(request)
                }

                override fun bekreftVilkårsvurderinger(
                    klageId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                ): Either<KunneIkkeBekrefteKlagesteg, VilkårsvurdertKlage.Bekreftet> {
                    assertHarTilgangTilKlage(klageId)
                    return services.klageService.bekreftVilkårsvurderinger(klageId, saksbehandler)
                }

                override fun vurder(request: KlageVurderingerRequest): Either<KunneIkkeVurdereKlage, VurdertKlage> {
                    assertHarTilgangTilKlage(request.klageId)
                    return services.klageService.vurder(request)
                }

                override fun bekreftVurderinger(
                    klageId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                ): Either<KunneIkkeBekrefteKlagesteg, VurdertKlage.Bekreftet> {
                    assertHarTilgangTilKlage(klageId)
                    return services.klageService.bekreftVurderinger(klageId, saksbehandler)
                }

                override fun leggTilAvvistFritekstTilBrev(
                    klageId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                    fritekst: String,
                ): Either<KunneIkkeLeggeTilFritekstForAvvist, AvvistKlage> {
                    assertHarTilgangTilKlage(klageId)
                    return services.klageService.leggTilAvvistFritekstTilBrev(klageId, saksbehandler, fritekst)
                }

                override fun sendTilAttestering(
                    klageId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                ): Either<KunneIkkeSendeTilAttestering, KlageTilAttestering> {
                    assertHarTilgangTilKlage(klageId)
                    return services.klageService.sendTilAttestering(klageId, saksbehandler)
                }

                override fun underkjenn(request: UnderkjennKlageRequest): Either<KunneIkkeUnderkjenne, Klage> {
                    assertHarTilgangTilKlage(request.klageId)
                    return services.klageService.underkjenn(request)
                }

                override fun oversend(
                    klageId: UUID,
                    attestant: NavIdentBruker.Attestant,
                ): Either<KunneIkkeOversendeKlage, OversendtKlage> {
                    assertHarTilgangTilKlage(klageId)
                    return services.klageService.oversend(klageId, attestant)
                }

                override fun iverksettAvvistKlage(
                    klageId: UUID,
                    attestant: NavIdentBruker.Attestant,
                ): Either<KunneIkkeIverksetteAvvistKlage, IverksattAvvistKlage> {
                    assertHarTilgangTilKlage(klageId)
                    return services.klageService.iverksettAvvistKlage(klageId, attestant)
                }

                override fun brevutkast(
                    klageId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                ): Either<KunneIkkeLageBrevutkast, ByteArray> {
                    assertHarTilgangTilKlage(klageId)
                    return services.klageService.brevutkast(klageId, saksbehandler)
                }

                override fun avslutt(
                    klageId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                    begrunnelse: String,
                ): Either<KunneIkkeAvslutteKlage, AvsluttetKlage> {
                    assertHarTilgangTilKlage(klageId)
                    return services.klageService.avslutt(
                        klageId = klageId,
                        saksbehandler = saksbehandler,
                        begrunnelse = begrunnelse,
                    )
                }
            },
            klageinstanshendelseService = object : KlageinstanshendelseService {
                override fun lagre(hendelse: UprosessertKlageinstanshendelse) = kastKanKunKallesFraAnnenService()
                override fun håndterUtfallFraKlageinstans(
                    deserializeAndMap: (id: UUID, opprettet: Tidspunkt, json: String) -> Either<KunneIkkeTolkeKlageinstanshendelse, TolketKlageinstanshendelse>,
                ) = kastKanKunKallesFraAnnenService()
            },
            reguleringService = object : ReguleringService {
                override fun startRegulering(startDato: LocalDate): List<Either<KunneIkkeOppretteRegulering, Regulering>> {
                    return services.reguleringService.startRegulering(startDato)
                }

                override fun avslutt(reguleringId: UUID): Either<KunneIkkeAvslutte, Regulering.AvsluttetRegulering> {
                    return services.reguleringService.avslutt(reguleringId)
                }

                override fun hentStatus(): List<Pair<Regulering, List<ReguleringMerknad>>> {
                    return services.reguleringService.hentStatus()
                }

                override fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer> {
                    return services.reguleringService.hentSakerMedÅpenBehandlingEllerStans()
                }

                override fun regulerManuelt(
                    reguleringId: UUID,
                    uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
                    fradrag: List<Grunnlag.Fradragsgrunnlag>,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                ): Either<KunneIkkeRegulereManuelt, Regulering.IverksattRegulering> {
                    return services.reguleringService.regulerManuelt(
                        reguleringId,
                        uføregrunnlag,
                        fradrag,
                        saksbehandler,
                    )
                }
            },
            tilbakekrevingService = object : TilbakekrevingService {
                override fun lagre(
                    tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag,
                ) = kastKanKunKallesFraAnnenService()

                override fun sendTilbakekrevingsvedtak(
                    mapper: (RåttKravgrunnlag) -> Kravgrunnlag,
                ) = kastKanKunKallesFraAnnenService()

                override fun hentAvventerKravgrunnlag(sakId: UUID) = kastKanKunKallesFraAnnenService()

                override fun hentAvventerKravgrunnlag(utbetalingId: UUID30) = kastKanKunKallesFraAnnenService()

                override fun hentAvventerKravgrunnlag() = kastKanKunKallesFraAnnenService()
            },
            sendPåminnelserOmNyStønadsperiodeService = object : SendPåminnelserOmNyStønadsperiodeService {
                override fun sendPåminnelser(): SendPåminnelseNyStønadsperiodeContext {
                    kastKanKunKallesFraAnnenService()
                }
            },
        )
    }

    /**
     * Denne skal kun brukes fra en annen service.
     * Når en service bruker en annen service, vil den ha den ikke-proxyede versjonen.
     * Vi kaster derfor her for å unngå at noen bruker metoden fra feil plass (som ville ha omgått tilgangssjekk).
     */
    private fun kastKanKunKallesFraAnnenService(): Nothing =
        throw IllegalStateException("This should only be called from another service")

    private fun assertHarTilgangTilPerson(fnr: Fnr) {
        services.person.sjekkTilgangTilPerson(fnr)
            .getOrHandle {
                throw Tilgangssjekkfeil(it, fnr)
            }
    }

    private fun assertHarTilgangTilSak(sakId: UUID) {
        personRepo.hentFnrForSak(sakId)
            .forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertHarTilgangTilSøknad(søknadId: UUID) {
        personRepo.hentFnrForSøknad(søknadId)
            .forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertHarTilgangTilBehandling(behandlingId: UUID) {
        personRepo.hentFnrForBehandling(behandlingId)
            .forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertHarTilgangTilUtbetaling(utbetalingId: UUID30) {
        personRepo.hentFnrForUtbetaling(utbetalingId)
            .forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertHarTilgangTilRevurdering(revurderingId: UUID) {
        personRepo.hentFnrForRevurdering(revurderingId)
            .forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertHarTilgangTilVedtak(vedtakId: UUID) {
        personRepo.hentFnrForVedtak(vedtakId)
            .forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertHarTilgangTilKlage(klageId: UUID) {
        personRepo.hentFnrForKlage(klageId)
            .forEach { assertHarTilgangTilPerson(it) }
    }
}

class Tilgangssjekkfeil(val feil: KunneIkkeHentePerson, val fnr: Fnr) : RuntimeException()
