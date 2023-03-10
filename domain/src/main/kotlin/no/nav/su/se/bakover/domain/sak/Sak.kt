package no.nav.su.se.bakover.domain

import arrow.core.Either
import arrow.core.Tuple4
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørTilOgMedDato
import no.nav.su.se.bakover.common.periode.Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden
import no.nav.su.se.bakover.common.periode.Periode.UgyldigPeriode.TilOgMedDatoMåVæreSisteDagIMåneden
import no.nav.su.se.bakover.common.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.hentOversendteUtbetalingslinjerUtenFeil
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.OpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.VurderOmVilkårGirOpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.StøtterIkkeOverlappendeStønadsperioder
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.beregningKanVæreGjeldende
import no.nav.su.se.bakover.domain.vedtak.hentBeregningForGjeldendeVedtak
import no.nav.su.se.bakover.domain.vedtak.lagTidslinje
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrerteUtenlandsopphold
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * @param uteståendeAvkorting er enten [Avkortingsvarsel.Ingen] eller [Avkortingsvarsel.Utenlandsopphold.SkalAvkortes]
 */
data class Sak(
    val id: UUID = UUID.randomUUID(),
    val saksnummer: Saksnummer,
    val opprettet: Tidspunkt,
    val fnr: Fnr,
    val søknader: List<Søknad> = emptyList(),
    val søknadsbehandlinger: List<Søknadsbehandling> = emptyList(),
    // TODO jah: Bytt til [Utbetaling.Oversendt]
    val utbetalinger: List<Utbetaling>,
    val revurderinger: List<AbstraktRevurdering> = emptyList(),
    val vedtakListe: List<Vedtak> = emptyList(),
    val klager: List<Klage> = emptyList(),
    val reguleringer: List<Regulering> = emptyList(),
    val type: Sakstype,
    val uteståendeAvkorting: Avkortingsvarsel = Avkortingsvarsel.Ingen,
    val utenlandsopphold: RegistrerteUtenlandsopphold = RegistrerteUtenlandsopphold.empty(id),
    val versjon: Hendelsesversjon,
) {
    init {
        require(uteståendeAvkorting is Avkortingsvarsel.Ingen || uteståendeAvkorting is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes)
    }

    fun info(): SakInfo {
        return SakInfo(
            sakId = id,
            saksnummer = saksnummer,
            fnr = fnr,
            type = type,
        )
    }

    fun utbetalingstidslinje(): TidslinjeForUtbetalinger? {
        return utbetalinger.hentOversendteUtbetalingslinjerUtenFeil().toNonEmptyListOrNull()?.let {
            TidslinjeForUtbetalinger(utbetalingslinjer = it)
        }
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

    sealed class KunneIkkeHenteGjeldendeVedtaksdata {
        data class FinnesIngenVedtakSomKanRevurderes(
            val fraOgMed: LocalDate,
            val tilOgMed: LocalDate,
        ) : KunneIkkeHenteGjeldendeVedtaksdata() {
            constructor(periode: Periode) : this(periode.fraOgMed, periode.tilOgMed)
            constructor(fraOgMed: LocalDate) : this(fraOgMed, LocalDate.MAX)
        }

        data class UgyldigPeriode(val feil: Periode.UgyldigPeriode) : KunneIkkeHenteGjeldendeVedtaksdata()
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

    sealed class KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak {
        object FantIkkeVedtak : KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak()
        object IngenTidligereVedtak : KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak()
    }

    /**
     * Brukes for å hente den seneste gjeldenden/brukte beregningen for en gitt måned i saken.
     *
     * Per nå så er det kun Vedtak i form av [VedtakSomKanRevurderes.EndringIYtelse] som bidrar til dette.
     *
     * ##NB
     * */
    fun hentGjeldendeBeregningForEndringIYtelsePåDato(
        måned: Måned,
        clock: Clock,
    ): Beregning? {
        return GjeldendeVedtaksdata(
            periode = måned,
            vedtakListe = vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
                .filter { it.beregningKanVæreGjeldende().isRight() }.ifEmpty { return null }.toNonEmptyList(),
            clock = clock,
        ).gjeldendeVedtakPåDato(måned.fraOgMed)?.hentBeregningForGjeldendeVedtak()
    }

    fun hentGjeldendeMånedsberegninger(
        periode: Periode,
        clock: Clock,
    ): List<Månedsberegning> {
        return GjeldendeVedtaksdata(
            periode = periode,
            vedtakListe = vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
                .filter { it.beregningKanVæreGjeldende().isRight() }.ifEmpty { return emptyList() }.toNonEmptyList(),
            clock = clock,
        ).let { gjeldendeVedtaksdata ->
            periode.måneder().mapNotNull { måned ->
                gjeldendeVedtaksdata.gjeldendeVedtakPåDato(måned.fraOgMed)?.hentBeregningForGjeldendeVedtak()
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

    fun hentRevurdering(id: UUID): Either<Unit, AbstraktRevurdering> {
        return revurderinger.singleOrNull { it.id == id }?.right() ?: Unit.left()
    }

    fun hentSøknadsbehandling(id: UUID): Either<Unit, Søknadsbehandling> {
        return søknadsbehandlinger.singleOrNull { it.id == id }?.right() ?: Unit.left()
    }

    /**
     * Henter minste antall sammenhengende perioder hvor vedtakene ikke er av typen opphør.
     */
    fun hentIkkeOpphørtePerioder(): List<Periode> {
        return vedtakstidslinje().tidslinje
            .filterNot { it.erOpphør() }
            .map { it.periode }
            .minsteAntallSammenhengendePerioder()
    }

    fun vedtakstidslinje(
        fraOgMed: Måned,
    ): Tidslinje<VedtakSomKanRevurderes.VedtakPåTidslinje> {
        return vedtakListe.filterIsInstance<VedtakSomKanRevurderes>().lagTidslinje(fraOgMed)
    }

    fun vedtakstidslinje(
        periode: Periode,
    ): Tidslinje<VedtakSomKanRevurderes.VedtakPåTidslinje> {
        return vedtakListe.filterIsInstance<VedtakSomKanRevurderes>().lagTidslinje(periode)
    }

    fun vedtakstidslinje(): Tidslinje<VedtakSomKanRevurderes.VedtakPåTidslinje> {
        return vedtakListe.filterIsInstance<VedtakSomKanRevurderes>().lagTidslinje()
    }

    /** Skal ikke kunne ha mer enn én åpen klage av gangen. */
    fun kanOppretteKlage(): Boolean = klager.none { it.erÅpen() }

    fun hentKlage(klageId: UUID): Klage? = klager.find { it.id == klageId }

    fun kanUtbetalingerStansesEllerGjenopptas(clock: Clock): KanStansesEllerGjenopptas {
        return utbetalingstidslinje()?.let {
            if (it.isNotEmpty() && ingenKommendeOpphørEllerHull(it, clock)) {
                if (it.last() is UtbetalingslinjePåTidslinje.Stans) {
                    KanStansesEllerGjenopptas.GJENOPPTA
                } else {
                    KanStansesEllerGjenopptas.STANS
                }
            } else {
                KanStansesEllerGjenopptas.INGEN
            }
        } ?: KanStansesEllerGjenopptas.INGEN
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

        if (kommendeUtbetalingslinjer.map { linje -> linje.periode }.minsteAntallSammenhengendePerioder().size > 1) {
            return false
        }

        return true
    }

    fun ytelseUtløperVedUtløpAv(periode: Periode): Boolean {
        return vedtakstidslinje().tidslinje.lastOrNull()?.let {
            !it.erOpphør() && it.periode slutterSamtidig periode
        } ?: false
    }

    sealed interface KunneIkkeOppretteEllerOppdatereRegulering {
        object FinnesIngenVedtakSomKanRevurderesForValgtPeriode : KunneIkkeOppretteEllerOppdatereRegulering
        object StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig : KunneIkkeOppretteEllerOppdatereRegulering
        object BleIkkeLagetReguleringDaDenneUansettMåRevurderes : KunneIkkeOppretteEllerOppdatereRegulering
    }

    fun hentSøknad(id: UUID): Either<FantIkkeSøknad, Søknad> {
        return søknader.singleOrNull { it.id == id }?.right() ?: FantIkkeSøknad.left()
    }

    object FantIkkeSøknad {
        override fun toString() = this::class.simpleName!!
    }

    fun hentÅpneSøknadsbehandlinger(): List<Søknadsbehandling> = søknadsbehandlinger.filter { it.erÅpen() }

    fun harÅpenSøknadsbehandling(): Boolean = hentÅpneSøknadsbehandlinger().isNotEmpty()

    fun harÅpenStansbehandling(): Boolean = revurderinger
        .filterIsInstance<StansAvYtelseRevurdering.SimulertStansAvYtelse>().isNotEmpty()

    fun harÅpenGjenopptaksbehandling(): Boolean = revurderinger
        .filterIsInstance<GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse>().isNotEmpty()

    fun hentUteståendeAvkortingForSøknadsbehandling(): Either<AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående, AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting> {
        return when (uteståendeAvkorting) {
            Avkortingsvarsel.Ingen -> {
                AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.left()
            }

            is Avkortingsvarsel.Utenlandsopphold.Annullert -> {
                AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.left()
            }

            is Avkortingsvarsel.Utenlandsopphold.Avkortet -> {
                AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.left()
            }

            is Avkortingsvarsel.Utenlandsopphold.Opprettet -> {
                AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.left()
            }

            is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes -> {
                AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(uteståendeAvkorting).right()
            }
        }
    }

    fun hentSøknadsbehandlingForSøknad(søknadId: UUID): Either<FantIkkeSøknadsbehandlingForSøknad, Søknadsbehandling> {
        return søknadsbehandlinger.singleOrNull { it.søknad.id == søknadId }?.right()
            ?: FantIkkeSøknadsbehandlingForSøknad.left()
    }

    sealed interface KunneIkkeOppretteSøknadsbehandling {
        object ErLukket : KunneIkkeOppretteSøknadsbehandling
        object ManglerOppgave : KunneIkkeOppretteSøknadsbehandling
        object FinnesAlleredeSøknadsehandlingForSøknad : KunneIkkeOppretteSøknadsbehandling
        object HarÅpenSøknadsbehandling : KunneIkkeOppretteSøknadsbehandling
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
        object FantIngenVedtakSomKanRevurderes : GjeldendeVedtaksdataErUgyldigForRevurdering
        object HeleRevurderingsperiodenInneholderIkkeVedtak : GjeldendeVedtaksdataErUgyldigForRevurdering
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
        object FormueSomFørerTilOpphørMåRevurderes : OpphørtVilkårMåRevurderes
        object UtenlandsoppholdSomFørerTilOpphørMåRevurderes : OpphørtVilkårMåRevurderes
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
        clock: Clock,
        hentPerson: () -> Either<KunneIkkeHentePerson, Person>,
        hentSaksbehandlerNavn: (saksbehandler: NavIdentBruker.Saksbehandler) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
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
            { søknadsbehandlingSomSkalLukkes ->
                // Finnes søknadsbehandling. Lukker søknadsbehandlingen, som i sin tur lukker søknaden.
                søknadsbehandlingSomSkalLukkes.lukkSøknadsbehandlingOgSøknad(
                    lukkSøknadCommand = lukkSøknadCommand,
                ).getOrElse {
                    throw IllegalArgumentException("Kunne ikke lukke søknad ${lukkSøknadCommand.søknadId} og søknadsbehandling. Underliggende feil: $it")
                }.let { lukketSøknadsbehandling ->
                    Tuple4(
                        this.copy(
                            søknader = this.søknader.filterNot { it.id == søknadId }
                                .plus(lukketSøknadsbehandling.søknad),
                            søknadsbehandlinger = this.søknadsbehandlinger.filterNot { it.id == lukketSøknadsbehandling.id }
                                .plus(lukketSøknadsbehandling),
                        ),
                        lukketSøknadsbehandling.søknad,
                        lukketSøknadsbehandling,
                        StatistikkEvent.Behandling.Søknad.Lukket(lukketSøknadsbehandling, saksbehandler),
                    )
                }
            },
        ).let { (sak, søknad, søknadsbehandling, statistikkhendelse) ->
            val lagBrevRequest = søknad.toBrevRequest(
                hentPerson = {
                    hentPerson().getOrElse {
                        throw IllegalStateException("Kunne ikke lukke søknad ${lukkSøknadCommand.søknadId} og søknadsbehandling. Underliggende grunn: $it")
                    }
                },
                clock = clock,
                hentSaksbehandlerNavn = {
                    hentSaksbehandlerNavn(it).getOrElse {
                        throw IllegalStateException("Kunne ikke lukke søknad ${lukkSøknadCommand.søknadId} og søknadsbehandling. Underliggende grunn: $it")
                    }
                },
            ) { sak.saksnummer }
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
        val lagBrevRequest: Either<IkkeLagBrevRequest, LagBrevRequest>,
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

        object IkkeLagBrevRequest
    }

    object FantIkkeSøknadsbehandlingForSøknad

    sealed interface KunneIkkeOppdatereStønadsperiode {
        object FantIkkeBehandling : KunneIkkeOppdatereStønadsperiode
        data class AldersvurderingGirIkkeRettPåUføre(val vurdering: Aldersvurdering) : KunneIkkeOppdatereStønadsperiode

        data class KunneIkkeOppdatereGrunnlagsdata(
            val feil: no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeOppdatereStønadsperiode,
        ) : KunneIkkeOppdatereStønadsperiode

        data class OverlappendeStønadsperiode(val feil: StøtterIkkeOverlappendeStønadsperioder) :
            KunneIkkeOppdatereStønadsperiode
    }

    fun avventerKravgrunnlag(): Boolean {
        return revurderinger.filterIsInstance<IverksattRevurdering>()
            .any { it.tilbakekrevingsbehandling.avventerKravgrunnlag() }
    }
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
    STANS, GJENOPPTA, INGEN,
    ;
}
