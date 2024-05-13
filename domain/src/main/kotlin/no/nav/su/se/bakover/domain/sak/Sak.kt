package no.nav.su.se.bakover.domain

import arrow.core.Either
import arrow.core.Tuple4
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.klage.domain.KlageId
import behandling.revurdering.domain.Opphørsgrunn
import beregning.domain.Beregning
import beregning.domain.Månedsberegning
import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.domain.tid.periode.SlåttSammenIkkeOverlappendePerioder
import no.nav.su.se.bakover.common.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.common.domain.whenever
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørTilOgMedDato
import no.nav.su.se.bakover.common.tid.periode.Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden
import no.nav.su.se.bakover.common.tid.periode.Periode.UgyldigPeriode.TilOgMedDatoMåVæreSisteDagIMåneden
import no.nav.su.se.bakover.domain.behandling.Behandlinger
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.regulering.Reguleringer
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.OpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.VurderOmVilkårGirOpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.sak.oppdaterSøknadsbehandling
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.StøtterIkkeOverlappendeStønadsperioder
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakPåTidslinje
import no.nav.su.se.bakover.domain.vedtak.lagTidslinje
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import vedtak.domain.Vedtak
import vedtak.domain.VedtakSomKanRevurderes
import vilkår.utenlandsopphold.domain.RegistrerteUtenlandsopphold
import økonomi.domain.utbetaling.TidslinjeForUtbetalinger
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import økonomi.domain.utbetaling.tidslinje
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class Sak(
    val id: UUID = UUID.randomUUID(),
    val saksnummer: Saksnummer,
    val opprettet: Tidspunkt,
    val fnr: Fnr,
    val søknader: List<Søknad> = emptyList(),
    val behandlinger: Behandlinger = Behandlinger.empty(id),
    // TODO jah: Bytt til [Utbetaling.Oversendt]
    val utbetalinger: Utbetalinger,
    val vedtakListe: List<Vedtak> = emptyList(),
    val type: Sakstype,
    val utenlandsopphold: RegistrerteUtenlandsopphold = RegistrerteUtenlandsopphold.empty(id),
    val versjon: Hendelsesversjon,
    val uteståendeKravgrunnlag: Kravgrunnlag?,
) {
    val søknadsbehandlinger: List<Søknadsbehandling> = behandlinger.søknadsbehandlinger
    val revurderinger: List<AbstraktRevurdering> = behandlinger.revurderinger
    val reguleringer: Reguleringer = behandlinger.reguleringer
    val klager: List<Klage> = behandlinger.klager

    fun info(): SakInfo {
        return SakInfo(
            sakId = id,
            saksnummer = saksnummer,
            fnr = fnr,
            type = type,
        )
    }

    fun utbetalingstidslinje(): TidslinjeForUtbetalinger? {
        return utbetalinger.tidslinje().getOrNull()
    }

    fun kopierGjeldendeVedtaksdata(
        fraOgMed: LocalDate,
        clock: Clock,
    ): Either<KunneIkkeHenteGjeldendeVedtaksdata, GjeldendeVedtaksdata> {
        return vedtakListe.filterIsInstance<VedtakSomKanRevurderes>().ifEmpty {
            return KunneIkkeHenteGjeldendeVedtaksdata.FinnesIngenVedtakSomKanRevurderes(fraOgMed).left()
        }.let { vedtakSomKanRevurderes ->
            val tilOgMed = vedtakSomKanRevurderes.maxOf { it.periode.tilOgMed }
            Periode.tryCreate(fraOgMed, tilOgMed).mapLeft {
                when (it) {
                    FraOgMedDatoMåVæreFørTilOgMedDato -> KunneIkkeHenteGjeldendeVedtaksdata.FinnesIngenVedtakSomKanRevurderes(
                        fraOgMed,
                        tilOgMed,
                    )

                    FraOgMedDatoMåVæreFørsteDagIMåneden, TilOgMedDatoMåVæreSisteDagIMåneden,
                    -> KunneIkkeHenteGjeldendeVedtaksdata.UgyldigPeriode(it)
                }
            }.flatMap {
                hentGjeldendeVedtaksdata(
                    periode = it,
                    clock = clock,
                )
            }
        }
    }

    fun hentGjeldendeVedtaksdata(
        periode: Periode,
        clock: Clock,
    ): Either<KunneIkkeHenteGjeldendeVedtaksdata.FinnesIngenVedtakSomKanRevurderes, GjeldendeVedtaksdata> {
        return vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
            .ifEmpty { return KunneIkkeHenteGjeldendeVedtaksdata.FinnesIngenVedtakSomKanRevurderes(periode).left() }
            .let { vedtakSomKanRevurderes ->
                GjeldendeVedtaksdata(
                    periode = periode,
                    vedtakListe = vedtakSomKanRevurderes.toNonEmptyList(),
                    clock = clock,
                ).right()
            }
    }

    sealed interface KunneIkkeHenteGjeldendeVedtaksdata {
        data class FinnesIngenVedtakSomKanRevurderes(
            val fraOgMed: LocalDate,
            val tilOgMed: LocalDate,
        ) : KunneIkkeHenteGjeldendeVedtaksdata {
            constructor(periode: Periode) : this(periode.fraOgMed, periode.tilOgMed)
            constructor(fraOgMed: LocalDate) : this(fraOgMed, LocalDate.MAX)
        }

        data class UgyldigPeriode(val feil: Periode.UgyldigPeriode) : KunneIkkeHenteGjeldendeVedtaksdata
    }

    /**
     * Lager et snapshot av gjeldende vedtaksdata slik de så ut før vedtaket angitt av [vedtakId] ble
     * iverksatt. Perioden for dataene begrenses til vedtaksperioden for [vedtakId].
     * Brukes av vedtaksoppsummeringen for å vise differansen mellom nytt/gammelt grunnlag.
     */
    fun historiskGrunnlagForVedtaketsPeriode(
        vedtakId: UUID,
        clock: Clock,
    ): Either<KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak, GjeldendeVedtaksdata> {
        val vedtak = vedtakListe.filterIsInstance<VedtakSomKanRevurderes>().find { it.id == vedtakId }
            ?: return KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak.FantIkkeVedtak.left()

        return vedtakListe.filterIsInstance<VedtakSomKanRevurderes>().filter { it.opprettet < vedtak.opprettet }
            .ifEmpty {
                return KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak.IngenTidligereVedtak.left()
            }.let {
                GjeldendeVedtaksdata(
                    periode = vedtak.periode,
                    vedtakListe = it.toNonEmptyList(),
                    clock = clock,
                ).right()
            }
    }

    sealed interface KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak {
        data object FantIkkeVedtak : KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak
        data object IngenTidligereVedtak : KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak
    }

    /**
     * Brukes for å hente den seneste gjeldenden/brukte beregningen for en gitt måned i saken.
     *
     * Per nå så er det kun Vedtak i form av [no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse] og [no.nav.su.se.bakover.domain.vedtak.VedtakIngenEndringIYtelse] som bidrar til dette.
     *
     * ##NB
     * */
    fun hentGjeldendeBeregningForEndringIYtelseForMåned(
        måned: Måned,
        clock: Clock,
    ): Beregning? {
        return GjeldendeVedtaksdata(
            periode = måned,
            vedtakListe = vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
                .filter { it.beregning != null }.ifEmpty { return null }.toNonEmptyList(),
            clock = clock,
        ).gjeldendeVedtakForMåned(måned)?.beregning!!
    }

    fun hentGjeldendeMånedsberegninger(
        periode: Periode,
        clock: Clock,
    ): List<Månedsberegning> {
        return GjeldendeVedtaksdata(
            periode = periode,
            vedtakListe = vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
                .filter { it.beregning != null }.ifEmpty { return emptyList() }.toNonEmptyList(),
            clock = clock,
        ).let { gjeldendeVedtaksdata ->
            periode.måneder().mapNotNull { måned ->
                gjeldendeVedtaksdata.gjeldendeVedtakForMåned(måned)?.beregning
                    ?.getMånedsberegninger()?.single { it.måned == måned }
            }
        }
    }

    fun hentGjeldendeStønadsperiode(clock: Clock): Periode? =
        hentIkkeOpphørtePerioder().filter { it.inneholder(LocalDate.now(clock)) }.maxByOrNull { it.tilOgMed }

    fun harGjeldendeEllerFremtidigStønadsperiode(clock: Clock): Boolean {
        val now = LocalDate.now(clock)
        return hentIkkeOpphørtePerioder().any { it.inneholder(now) || it.starterEtter(now) }
    }

    fun hentRevurdering(id: RevurderingId): Either<Unit, AbstraktRevurdering> {
        return revurderinger.singleOrNull { it.id == id }?.right() ?: Unit.left()
    }

    fun hentSøknadsbehandling(id: SøknadsbehandlingId): Either<Unit, Søknadsbehandling> {
        return søknadsbehandlinger.singleOrNull { it.id == id }?.right() ?: Unit.left()
    }

    /**
     * Henter minste antall sammenhengende perioder hvor vedtakene ikke er av typen opphør.
     */
    fun hentIkkeOpphørtePerioder(): SlåttSammenIkkeOverlappendePerioder =
        vedtakstidslinje()
            ?.filterNot { it.erOpphør() }
            ?.map { it.periode }
            ?.minsteAntallSammenhengendePerioder()
            ?: EmptyPerioder

    fun vedtakstidslinje(
        fraOgMed: Måned,
    ): Tidslinje<VedtakPåTidslinje>? =
        vedtakListe.filterIsInstance<VedtakSomKanRevurderes>().lagTidslinje()?.fjernMånederFør(fraOgMed)

    fun vedtakstidslinje(): Tidslinje<VedtakPåTidslinje>? =
        vedtakListe.filterIsInstance<VedtakSomKanRevurderes>().lagTidslinje()

    /** Skal ikke kunne ha mer enn én åpen klage av gangen. */
    fun kanOppretteKlage(): Boolean = klager.none { it.erÅpen() }

    fun hentKlage(klageId: KlageId): Klage? = klager.find { it.id == klageId }

    fun kanUtbetalingerStansesEllerGjenopptas(clock: Clock): KanStansesEllerGjenopptas {
        val tidslinje = utbetalingstidslinje()
        if (tidslinje.isNullOrEmpty()) return KanStansesEllerGjenopptas.INGEN
        if (!ingenKommendeOpphørEllerHull(tidslinje, clock)) return KanStansesEllerGjenopptas.INGEN
        val last = tidslinje.last()
        if (last is UtbetalingslinjePåTidslinje.Stans) {
            return KanStansesEllerGjenopptas.GJENOPPTA
        }
        if (last is UtbetalingslinjePåTidslinje.Ny || last is UtbetalingslinjePåTidslinje.Reaktivering) {
            return KanStansesEllerGjenopptas.STANS
        }
        return KanStansesEllerGjenopptas.INGEN
    }

    /**
     * Tillater ikke stans dersom stønadsperiodene inneholder opphør eller hull frem i tid, siden det kan bli
     * problematisk hvis man stanser og gjenopptar på tvers av disse. Ta opp igjen problemstillingen dersom
     * fag trenger å stanse i et slikt tilfelle.
     */
    private fun ingenKommendeOpphørEllerHull(
        utbetalingslinjer: List<UtbetalingslinjePåTidslinje>,
        clock: Clock,
    ): Boolean {
        val kommendeUtbetalingslinjer = utbetalingslinjer.filter { it.periode.tilOgMed.isAfter(LocalDate.now(clock)) }

        if (kommendeUtbetalingslinjer.any { it is UtbetalingslinjePåTidslinje.Opphør }) {
            return false
        }

        return kommendeUtbetalingslinjer.map { linje -> linje.periode }.minsteAntallSammenhengendePerioder().size <= 1
    }

    fun ytelseUtløperVedUtløpAv(periode: Periode): Boolean {
        return vedtakstidslinje()?.lastOrNull()?.let {
            !it.erOpphør() && it.periode slutterSamtidig periode
        } ?: false
    }

    sealed interface KunneIkkeOppretteEllerOppdatereRegulering {
        data object FinnesIngenVedtakSomKanRevurderesForValgtPeriode : KunneIkkeOppretteEllerOppdatereRegulering
        data object StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig : KunneIkkeOppretteEllerOppdatereRegulering
        data object BleIkkeLagetReguleringDaDenneUansettMåRevurderes : KunneIkkeOppretteEllerOppdatereRegulering
    }

    fun hentSøknad(id: UUID): Either<FantIkkeSøknad, Søknad> {
        return søknader.singleOrNull { it.id == id }?.right() ?: FantIkkeSøknad.left()
    }

    data object FantIkkeSøknad

    fun hentÅpneSøknadsbehandlinger(): List<Søknadsbehandling> = søknadsbehandlinger.filter { it.erÅpen() }

    fun harÅpenSøknadsbehandling(): Boolean = hentÅpneSøknadsbehandlinger().isNotEmpty()

    fun harÅpenStansbehandling(): Boolean = revurderinger
        .filterIsInstance<StansAvYtelseRevurdering.SimulertStansAvYtelse>().isNotEmpty()

    fun harÅpenGjenopptaksbehandling(): Boolean = revurderinger
        .filterIsInstance<GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse>().isNotEmpty()

    fun hentSøknadsbehandlingForSøknad(søknadId: UUID): Either<FantIkkeSøknadsbehandlingForSøknad, List<Søknadsbehandling>> {
        return søknadsbehandlinger.filter { it.søknad.id == søknadId }.whenever(
            { FantIkkeSøknadsbehandlingForSøknad.left() },
            { it.right() },
        )
    }

    internal fun hentGjeldendeVedtaksdataOgSjekkGyldighetForRevurderingsperiode(
        periode: Periode,
        clock: Clock,
    ): Either<GjeldendeVedtaksdataErUgyldigForRevurdering, GjeldendeVedtaksdata> {
        return hentGjeldendeVedtaksdata(
            periode = periode,
            clock = clock,
        ).getOrElse {
            return GjeldendeVedtaksdataErUgyldigForRevurdering.FantIngenVedtakSomKanRevurderes.left()
        }.let {
            if (!it.harVedtakIHelePerioden()) {
                return GjeldendeVedtaksdataErUgyldigForRevurdering.HeleRevurderingsperiodenInneholderIkkeVedtak.left()
            }
            it.right()
        }
    }

    sealed interface GjeldendeVedtaksdataErUgyldigForRevurdering {
        data object FantIngenVedtakSomKanRevurderes : GjeldendeVedtaksdataErUgyldigForRevurdering
        data object HeleRevurderingsperiodenInneholderIkkeVedtak : GjeldendeVedtaksdataErUgyldigForRevurdering
    }

    /**
     * TODO Vurder å implementer alle varianter eller bytte ut hele mekanismen
     * Brukes til å varsle om at vilkår man ikke har valgt å revurdere vil gir opphør. Avverger bla. at man havner
     * på oppsummeringen uten å ane hva som fører til opphør. Hvis mekanismen skal leve videre bør den utvides med alle
     * manglende vilkår, alternativt kan den erstattes med noe annet som f.eks at man alltid har muligheten til å
     * finne vilkårene på oppsummeringssiden (også de som ikke ble revurdert aktivt av saksbehandler) eller lignende.
     */
    internal fun InformasjonSomRevurderes.sjekkAtOpphørteVilkårRevurderes(gjeldendeVedtaksdata: GjeldendeVedtaksdata): Either<OpphørtVilkårMåRevurderes, Unit> {
        return VurderOmVilkårGirOpphørVedRevurdering(gjeldendeVedtaksdata.vilkårsvurderinger).resultat.let {
            when (it) {
                is OpphørVedRevurdering.Ja -> {
                    if (!harValgtFormue() && it.opphørsgrunner.contains(Opphørsgrunn.FORMUE)) {
                        OpphørtVilkårMåRevurderes.FormueSomFørerTilOpphørMåRevurderes.left()
                    }
                    if (!harValgtUtenlandsopphold() && it.opphørsgrunner.contains(Opphørsgrunn.UTENLANDSOPPHOLD)) {
                        OpphørtVilkårMåRevurderes.UtenlandsoppholdSomFørerTilOpphørMåRevurderes.left()
                    }
                    Unit.right()
                }

                is OpphørVedRevurdering.Nei -> {
                    Unit.right()
                }
            }
        }
    }

    sealed interface OpphørtVilkårMåRevurderes {
        data object FormueSomFørerTilOpphørMåRevurderes : OpphørtVilkårMåRevurderes
        data object UtenlandsoppholdSomFørerTilOpphørMåRevurderes : OpphørtVilkårMåRevurderes
    }

    /**
     * @return Den oppdaterte saken, søknaden som er lukket og dersom den fantes, den lukkede søknadsbehandlingen.
     *
     * @throws IllegalArgumentException dersom søknadId ikke finnes på saken
     * @throws IllegalArgumentException dersom søknaden ikke er i tilstanden [Søknad.Journalført.MedOppgave.IkkeLukket]
     * @throws IllegalStateException dersom noe uventet skjer ved lukking av søknad/søknadsbehandling
     */
    fun lukkSøknadOgSøknadsbehandling(
        lukkSøknadCommand: LukkSøknadCommand,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): LukkSøknadOgSøknadsbehandlingResponse {
        val søknadId = lukkSøknadCommand.søknadId
        val søknad = hentSøknad(søknadId).getOrElse {
            throw IllegalArgumentException("Kunne ikke lukke søknad og søknadsbehandling. Fant ikke søknad for sak $id og søknad $søknadId. Underliggende feil: $it")
        }
        return hentSøknadsbehandlingForSøknad(søknadId).fold(
            {
                // Finnes ingen søknadsbehandling. Lukker kun søknaden.
                val lukketSøknad = søknad.lukk(
                    lukkSøknadCommand = lukkSøknadCommand,
                )
                Tuple4(
                    this.copy(
                        søknader = this.søknader.filterNot { it.id == søknadId }.plus(lukketSøknad),
                    ),
                    lukketSøknad,
                    null,
                    StatistikkEvent.Søknad.Lukket(lukketSøknad, saksnummer),
                )
            },
            { søknadensBehandlinger ->
                // det skal kun finnes 1 søknadsbehandling åpen om gangen
                val søknadsbehandlingSomSkalLukkes =
                    søknadensBehandlinger.singleOrNull { it.søknad.id == søknadId && it.erÅpen() }
                        ?: throw IllegalStateException("Fant ingen, eller flere åpne søknadsbehandlinger for søknad $søknadId. Antall behandlinger funnet ${søknadensBehandlinger.size}")

                // Finnes søknadsbehandling. Lukker søknadsbehandlingen, som i sin tur lukker søknaden.
                søknadsbehandlingSomSkalLukkes.lukkSøknadsbehandlingOgSøknad(
                    lukkSøknadCommand = lukkSøknadCommand,
                ).getOrElse {
                    throw IllegalArgumentException("Kunne ikke lukke søknad ${lukkSøknadCommand.søknadId} og søknadsbehandling. Underliggende feil: $it")
                }.let { lukketSøknadsbehandling ->
                    Tuple4(
                        this.oppdaterSøknadsbehandling(lukketSøknadsbehandling)
                            .copy(
                                søknader = this.søknader.filterNot { it.id == søknadId }
                                    .plus(lukketSøknadsbehandling.søknad),
                            ),
                        lukketSøknadsbehandling.søknad,
                        lukketSøknadsbehandling,
                        StatistikkEvent.Behandling.Søknad.Lukket(lukketSøknadsbehandling, saksbehandler),
                    )
                }
            },
        ).let { (sak, søknad, søknadsbehandling, statistikkhendelse) ->
            val lagBrevRequest = søknad.lagGenererDokumentKommando { sak.saksnummer }
            LukkSøknadOgSøknadsbehandlingResponse(
                sak = sak,
                søknad = søknad,
                søknadsbehandling = søknadsbehandling,
                hendelse = statistikkhendelse,
                lagBrevRequest = lagBrevRequest.mapLeft { LukkSøknadOgSøknadsbehandlingResponse.IkkeLagBrevRequest },
            )
        }
    }

    /**
     * @param søknadsbehandling null dersom det ikke eksisterer en søknadsbehandling
     * @param lagBrevRequest null dersom vi ikke skal lage brev
     */
    data class LukkSøknadOgSøknadsbehandlingResponse(
        val sak: Sak,
        val søknad: Søknad.Journalført.MedOppgave.Lukket,
        val søknadsbehandling: LukketSøknadsbehandling?,
        val hendelse: StatistikkEvent,
        val lagBrevRequest: Either<IkkeLagBrevRequest, GenererDokumentCommand>,
    ) {
        init {
            // Guards spesielt med tanke på testdatasett.
            require(
                hendelse is StatistikkEvent.Behandling.Søknad.Lukket || hendelse is StatistikkEvent.Søknad.Lukket,
            )
            lagBrevRequest.onRight {
                require(it.saksnummer == sak.saksnummer)
            }
            require(sak.hentSøknad(søknad.id).getOrNull()!! == søknad)
            søknadsbehandling?.also {
                require(sak.søknadsbehandlinger.contains(søknadsbehandling))
                require(søknadsbehandling.søknad == søknad)
            }
        }

        data object IkkeLagBrevRequest
    }

    data object FantIkkeSøknadsbehandlingForSøknad

    sealed interface KunneIkkeOppdatereStønadsperiode {
        data object FantIkkeBehandling : KunneIkkeOppdatereStønadsperiode
        data class AldersvurderingGirIkkeRettPåUføre(val vurdering: Aldersvurdering) : KunneIkkeOppdatereStønadsperiode

        data class KunneIkkeOppdatereGrunnlagsdata(
            val feil: no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.KunneIkkeOppdatereStønadsperiode,
        ) : KunneIkkeOppdatereStønadsperiode

        data class OverlappendeStønadsperiode(val feil: StøtterIkkeOverlappendeStønadsperioder) :
            KunneIkkeOppdatereStønadsperiode
    }

    /**
     * fraOgMed fra det første søknadsbehandlingsvedtaket, null hvis vi ikke har noen vedtak enda.
     */
    val førsteYtelsesdato: LocalDate? = vedtakListe
        .filterIsInstance<VedtakInnvilgetSøknadsbehandling>()
        .minOfOrNull { it.periode.fraOgMed }
}

data class AlleredeGjeldendeSakForBruker(
    val uføre: BegrensetSakinfo,
    val alder: BegrensetSakinfo,
)

data class BegrensetSakinfo(
    val harÅpenSøknad: Boolean,
    val iverksattInnvilgetStønadsperiode: Periode?,
)

enum class KanStansesEllerGjenopptas {
    STANS,
    GJENOPPTA,
    INGEN,
}
