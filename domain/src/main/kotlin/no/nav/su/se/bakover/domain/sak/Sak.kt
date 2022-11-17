package no.nav.su.se.bakover.domain

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.Tuple4
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
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
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
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
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.VurderOmVilkårGirOpphørVedRevurdering
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.beregningKanVæreGjeldende
import no.nav.su.se.bakover.domain.vedtak.hentBeregningForGjeldendeVedtak
import no.nav.su.se.bakover.domain.vedtak.lagTidslinje
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrerteUtenlandsopphold
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class Sak(
    val id: UUID = UUID.randomUUID(),
    val saksnummer: Saksnummer,
    val opprettet: Tidspunkt,
    val fnr: Fnr,
    val søknader: List<Søknad> = emptyList(),
    val søknadsbehandlinger: List<Søknadsbehandling> = emptyList(),
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
    private val log = LoggerFactory.getLogger(this::class.java)

    fun info(): SakInfo {
        return SakInfo(
            sakId = id,
            saksnummer = saksnummer,
            fnr = fnr,
            type = type,
        )
    }

    fun utbetalingstidslinje(
        periode: Periode = Periode.create(
            fraOgMed = LocalDate.MIN,
            tilOgMed = LocalDate.MAX,
        ),
    ): TidslinjeForUtbetalinger {
        return TidslinjeForUtbetalinger(
            periode = periode,
            utbetalingslinjer = utbetalinger.hentOversendteUtbetalingslinjerUtenFeil(),
        )
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
     * Per nå så er det kun Vedtak i form av [VedtakSomKanRevurderes.EndringIYtelse] som bidrar til dette, bortsett fra [VedtakSomKanRevurderes.IngenEndringIYtelse] som har
     * andre beregnings-beløp som ikke skal ha en påverkan på saken.
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
        hentPerioderMedLøpendeYtelse().filter { it.inneholder(LocalDate.now(clock)) }.maxByOrNull { it.tilOgMed }

    fun harGjeldendeEllerFremtidigStønadsperiode(clock: Clock): Boolean {
        val now = LocalDate.now(clock)
        return hentPerioderMedLøpendeYtelse().any { it.inneholder(now) || it.starterEtter(now) }
    }

    fun harÅpenRevurderingForStansAvYtelse(): Boolean {
        return revurderinger.filterIsInstance<StansAvYtelseRevurdering.SimulertStansAvYtelse>().isNotEmpty()
    }

    fun harÅpenRevurderingForGjenopptakAvYtelse(): Boolean {
        return revurderinger.filterIsInstance<GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse>().isNotEmpty()
    }

    fun hentRevurdering(id: UUID): Either<Unit, AbstraktRevurdering> {
        return revurderinger.singleOrNull { it.id == id }?.right() ?: Unit.left()
    }

    fun hentSøknadsbehandling(id: UUID): Either<Unit, Søknadsbehandling> {
        return søknadsbehandlinger.singleOrNull { it.id == id }?.right() ?: Unit.left()
    }

    /**
     * Identifiser alle perioder hvor ytelsen har vært eller vil være løpende.
     */
    fun hentPerioderMedLøpendeYtelse(
        periode: Periode = Periode.create(
            fraOgMed = LocalDate.MIN,
            tilOgMed = LocalDate.MAX,
        ),
    ): List<Periode> {
        return vedtakstidslinje(periode = periode).tidslinje.filterNot { it.erOpphør() }.map { it.periode }
            .minsteAntallSammenhengendePerioder()
    }

    fun vedtakstidslinje(
        periode: Periode = Periode.create(
            fraOgMed = LocalDate.MIN,
            tilOgMed = LocalDate.MAX,
        ),
    ): Tidslinje<VedtakSomKanRevurderes.VedtakPåTidslinje> {
        return vedtakListe.filterIsInstance<VedtakSomKanRevurderes>().lagTidslinje(periode)
    }

    /** Skal ikke kunne ha mer enn én åpen klage av gangen. */
    fun kanOppretteKlage(): Boolean = klager.none { it.erÅpen() }

    fun hentKlage(klageId: UUID): Klage? = klager.find { it.id == klageId }

    fun kanUtbetalingerStansesEllerGjenopptas(clock: Clock): KanStansesEllerGjenopptas {
        return utbetalingstidslinje().tidslinje.let {
            if (it.isNotEmpty() && ingenKommendeOpphørEllerHull(it, clock)) {
                if (it.last() is UtbetalingslinjePåTidslinje.Stans) {
                    KanStansesEllerGjenopptas.GJENOPPTA
                } else {
                    KanStansesEllerGjenopptas.STANS
                }
            } else {
                KanStansesEllerGjenopptas.INGEN
            }
        }
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

    fun oppdaterStønadsperiodeForSøknadsbehandling(
        søknadsbehandlingId: UUID,
        stønadsperiode: Stønadsperiode,
        clock: Clock,
        formuegrenserFactory: FormuegrenserFactory,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeOppdatereStønadsperiode, Pair<Sak, Søknadsbehandling.Vilkårsvurdert>> {
        val søknadsbehandling = søknadsbehandlinger.singleOrNull {
            it.id == søknadsbehandlingId
        } ?: return KunneIkkeOppdatereStønadsperiode.FantIkkeBehandling.left()

        hentPerioderMedLøpendeYtelse().let { stønadsperioder ->
            if (stønadsperioder.any { it overlapper stønadsperiode.periode }) {
                return KunneIkkeOppdatereStønadsperiode.StønadsperiodeOverlapperMedLøpendeStønadsperiode.left()
            }
            if (stønadsperioder.any { it.starterSamtidigEllerSenere(stønadsperiode.periode) }) {
                return KunneIkkeOppdatereStønadsperiode.StønadsperiodeForSenerePeriodeEksisterer.left()
            }
        }

        hentGjeldendeVedtaksdata(
            periode = stønadsperiode.periode,
            clock = clock,
        ).map {
            if (it.inneholderOpphørsvedtakMedAvkortingUtenlandsopphold()) {
                val alleUtbetalingerErOpphørt =
                    utbetalingstidslinje(periode = stønadsperiode.periode).tidslinje.all { utbetalingslinjePåTidslinje ->
                        utbetalingslinjePåTidslinje is UtbetalingslinjePåTidslinje.Opphør
                    }

                if (!alleUtbetalingerErOpphørt) {
                    return KunneIkkeOppdatereStønadsperiode.StønadsperiodeInneholderAvkortingPgaUtenlandsopphold.left()
                }
            }
        }

        return søknadsbehandling.oppdaterStønadsperiode(
            oppdatertStønadsperiode = stønadsperiode,
            formuegrenserFactory = formuegrenserFactory,
            clock = clock,
            saksbehandler = saksbehandler,
        ).mapLeft {
            when (it) {
                is no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata -> {
                    KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
                }

                is no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeOppdatereStønadsperiode.UgyldigTilstand -> {
                    KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
                }
            }
        }.map { søknadsbehandlingMedOppdatertStønadsperiode ->
            Pair(
                this.copy(
                    søknadsbehandlinger = søknadsbehandlinger.filterNot { it.id == søknadsbehandlingMedOppdatertStønadsperiode.id } + søknadsbehandlingMedOppdatertStønadsperiode,
                ),
                søknadsbehandlingMedOppdatertStønadsperiode,
            )
        }
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
        object HarÅpenBehandling : KunneIkkeOppretteEllerOppdatereRegulering
    }

    /**
     * Iverksatte regulering vil ikke bli oppdatert
     *
     * @return Dersom Either.Left: Disse skal det ikke lages noen regulering for. Denne funksjonen har logget.
     */
    fun opprettEllerOppdaterRegulering(
        // TODO jah: Bytt til YearMonth (Da slipper vi en unødvendig left)
        startDato: LocalDate,
        clock: Clock,
    ): Either<KunneIkkeOppretteEllerOppdatereRegulering, Regulering.OpprettetRegulering> {
        val (reguleringsId, opprettet, _startDato) = reguleringer.filterIsInstance<Regulering.OpprettetRegulering>()
            .let { r ->
                when (r.size) {
                    0 -> Triple(UUID.randomUUID(), Tidspunkt.now(clock), startDato).also {
                        if (!kanOppretteBehandling()) {
                            return KunneIkkeOppretteEllerOppdatereRegulering.HarÅpenBehandling.left()
                        }
                    }

                    1 -> Triple(r.first().id, r.first().opprettet, minOf(startDato, r.first().periode.fraOgMed)).also {
                        if (harÅpenSøknadsbehandling() || harÅpenRevurdering()) {
                            return KunneIkkeOppretteEllerOppdatereRegulering.HarÅpenBehandling.left()
                        }
                    }

                    else -> throw IllegalStateException("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende grunn: Det finnes fler enn en åpen regulering.")
                }
            }

        val periode = vedtakstidslinje(
            periode = Periode.create(
                fraOgMed = _startDato,
                tilOgMed = LocalDate.MAX,
            ),
        ).tidslinje.let { tidslinje ->
            tidslinje.filterNot { it.erOpphør() }.map { vedtakUtenOpphør -> vedtakUtenOpphør.periode }
                .minsteAntallSammenhengendePerioder().ifEmpty {
                    log.info("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende feil: Har ingen vedtak å regulere fra og med $_startDato")
                    return KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode.left()
                }
        }.also {
            if (it.count() != 1) return KunneIkkeOppretteEllerOppdatereRegulering.StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig.left()
        }.single()

        val gjeldendeVedtaksdata = this.hentGjeldendeVedtaksdata(periode = periode, clock = clock).getOrHandle { feil ->
            log.info("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende feil: Har ingen vedtak å regulere for perioden (${feil.fraOgMed}, ${feil.tilOgMed})")
            return KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode.left()
        }

        return Regulering.opprettRegulering(
            id = reguleringsId,
            opprettet = opprettet,
            sakId = id,
            saksnummer = saksnummer,
            fnr = fnr,
            gjeldendeVedtaksdata = gjeldendeVedtaksdata,
            clock = clock,
            sakstype = type,
        ).mapLeft {
            KunneIkkeOppretteEllerOppdatereRegulering.BleIkkeLagetReguleringDaDenneUansettMåRevurderes
        }
    }

    fun hentSøknad(id: UUID): Either<FantIkkeSøknad, Søknad> {
        return søknader.singleOrNull { it.id == id }?.right() ?: FantIkkeSøknad.left()
    }

    object FantIkkeSøknad {
        override fun toString() = this::class.simpleName!!
    }

    fun hentÅpneSøknadsbehandlinger(): Either<IngenÅpneSøknadsbehandlinger, NonEmptyList<Søknadsbehandling>> {
        return søknadsbehandlinger.filter { it.erÅpen() }.ifEmpty { return IngenÅpneSøknadsbehandlinger.left() }
            .toNonEmptyList().right()
    }

    fun harÅpenSøknadsbehandling(): Boolean {
        return hentÅpneSøknadsbehandlinger().fold(
            { false },
            { it.isNotEmpty() },
        )
    }

    object IngenÅpneSøknadsbehandlinger

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

    fun hentUteståendeAvkortingForRevurdering(): Either<AvkortingVedRevurdering.Uhåndtert.IngenUtestående, AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting> {
        return when (uteståendeAvkorting) {
            is Avkortingsvarsel.Ingen -> {
                AvkortingVedRevurdering.Uhåndtert.IngenUtestående.left()
            }

            is Avkortingsvarsel.Utenlandsopphold.Annullert -> {
                AvkortingVedRevurdering.Uhåndtert.IngenUtestående.left()
            }

            is Avkortingsvarsel.Utenlandsopphold.Avkortet -> {
                AvkortingVedRevurdering.Uhåndtert.IngenUtestående.left()
            }

            is Avkortingsvarsel.Utenlandsopphold.Opprettet -> {
                AvkortingVedRevurdering.Uhåndtert.IngenUtestående.left()
            }

            is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes -> {
                AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(uteståendeAvkorting).right()
            }
        }
    }

    fun hentSøknadsbehandlingForSøknad(søknadId: UUID): Either<FantIkkeSøknadsbehandlingForSøknad, Søknadsbehandling> {
        return søknadsbehandlinger.singleOrNull { it.søknad.id == søknadId }?.right()
            ?: FantIkkeSøknadsbehandlingForSøknad.left()
    }

    sealed interface KunneIkkeOppretteSøknad {
        object FantIkkeSøknad : KunneIkkeOppretteSøknad
        object HarÅpenBehandling : KunneIkkeOppretteSøknad
        object ErLukket : KunneIkkeOppretteSøknad
        object ManglerOppgave : KunneIkkeOppretteSøknad
        object HarAlleredeBehandling : KunneIkkeOppretteSøknad
    }

    fun opprettNySøknadsbehandling(
        søknadId: UUID,
        clock: Clock,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeOppretteSøknad, NySøknadsbehandling> = if (!kanOppretteBehandling()) {
        KunneIkkeOppretteSøknad.HarÅpenBehandling.left()
    } else {
        val søknad = hentSøknad(søknadId).fold(
            ifLeft = { return KunneIkkeOppretteSøknad.FantIkkeSøknad.left() },
            ifRight = {
                if (it is Søknad.Journalført.MedOppgave.Lukket) {
                    return KunneIkkeOppretteSøknad.ErLukket.left()
                }
                if (it !is Søknad.Journalført.MedOppgave) {
                    // TODO Prøv å opprette oppgaven hvis den mangler? (systembruker blir kanskje mest riktig?)
                    return KunneIkkeOppretteSøknad.ManglerOppgave.left()
                }
                if (hentSøknadsbehandlingForSøknad(søknadId).isNotEmpty()) {
                    return KunneIkkeOppretteSøknad.HarAlleredeBehandling.left()
                }
                it
            },
        )

        NySøknadsbehandling(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            sakId = this.id,
            søknad = søknad,
            oppgaveId = søknad.oppgaveId,
            fnr = søknad.fnr,
            avkorting = this.hentUteståendeAvkortingForSøknadsbehandling().fold({ it }, { it }).kanIkke(),
            sakstype = søknad.type,
            saksbehandler = saksbehandler,
        ).right()
    }

    internal fun kontrollerAtUteståendeAvkortingRevurderes(
        periode: Periode,
        uteståendeAvkorting: AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting,
    ): Either<Unit, AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting> {
        return if (!periode.inneholder(uteståendeAvkorting.avkortingsvarsel.periode())) Unit.left() else uteståendeAvkorting.right()
    }

    fun oppdaterRevurdering(
        revurderingId: UUID,
        periode: Periode,
        saksbehandler: NavIdentBruker.Saksbehandler,
        revurderingsårsak: Revurderingsårsak,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        clock: Clock,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
        val revurdering = hentRevurdering(revurderingId).fold(
            { return KunneIkkeOppdatereRevurdering.FantIkkeRevurdering.left() },
            {
                if (it is Revurdering) it else return KunneIkkeOppdatereRevurdering.FantIkkeRevurdering.left()
            },
        )

        val gjeldendeVedtaksdata = hentGjeldendeVedtaksdataOgSjekkGyldighetForRevurderingsperiode(
            periode = periode,
            clock = clock,
        ).getOrHandle {
            return KunneIkkeOppdatereRevurdering.GjeldendeVedtaksdataKanIkkeRevurderes(it).left()
        }

        informasjonSomRevurderes.sjekkAtOpphørteVilkårRevurderes(gjeldendeVedtaksdata)
            .getOrHandle { return KunneIkkeOppdatereRevurdering.OpphørteVilkårMåRevurderes(it).left() }

        val avkorting = hentUteståendeAvkortingForRevurdering().fold(
            {
                it
            },
            { uteståendeAvkorting ->
                kontrollerAtUteståendeAvkortingRevurderes(
                    periode = periode,
                    uteståendeAvkorting = uteståendeAvkorting,
                ).getOrHandle {
                    return KunneIkkeOppdatereRevurdering.UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(
                        periode = uteståendeAvkorting.avkortingsvarsel.periode(),
                    ).left()
                }
            },
        )

        return revurdering.oppdater(
            periode = periode,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
            vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(dato = periode.fraOgMed)!!.id,
            avkorting = avkorting,
            saksbehandler = saksbehandler,
        ).mapLeft {
            KunneIkkeOppdatereRevurdering.KunneIkkeOppdatere(it)
        }
    }

    sealed class KunneIkkeOppdatereRevurdering {
        object FantIkkeSak : KunneIkkeOppdatereRevurdering()
        object FantIkkeRevurdering : KunneIkkeOppdatereRevurdering()
        data class UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(val periode: Periode) :
            KunneIkkeOppdatereRevurdering()

        data class KunneIkkeOppdatere(val feil: Revurdering.KunneIkkeOppdatereRevurdering) :
            KunneIkkeOppdatereRevurdering()

        data class GjeldendeVedtaksdataKanIkkeRevurderes(val feil: GjeldendeVedtaksdataErUgyldigForRevurdering) :
            KunneIkkeOppdatereRevurdering()

        data class OpphørteVilkårMåRevurderes(val feil: OpphørtVilkårMåRevurderes) : KunneIkkeOppdatereRevurdering()
    }

    internal fun hentGjeldendeVedtaksdataOgSjekkGyldighetForRevurderingsperiode(
        periode: Periode,
        clock: Clock,
    ): Either<GjeldendeVedtaksdataErUgyldigForRevurdering, GjeldendeVedtaksdata> {
        return hentGjeldendeVedtaksdata(
            periode = periode,
            clock = clock,
        ).getOrHandle {
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

    fun harÅpenRegulering() = hentÅpneReguleringer().isRight()
    fun hentÅpneReguleringer(): Either<IngenÅpneReguleringer, NonEmptyList<Regulering>> =
        reguleringer.filter { it.erÅpen() }.ifEmpty { return IngenÅpneReguleringer.left() }.toNonEmptyList().right()

    fun harÅpenRevurdering() = hentÅpneRevurderinger().isRight()
    fun hentÅpneRevurderinger(): Either<IngenÅpneRevurderinger, NonEmptyList<AbstraktRevurdering>> =
        revurderinger.filter { it.erÅpen() }.ifEmpty { return IngenÅpneRevurderinger.left() }.toNonEmptyList().right()

    fun kanOppretteBehandling(): Boolean =
        !harÅpenSøknadsbehandling() && !harÅpenRevurdering() && !harÅpenRegulering() && !harÅpenRevurderingForStansAvYtelse() && !harÅpenRevurderingForGjenopptakAvYtelse()

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
        val søknad = hentSøknad(søknadId).getOrHandle {
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
                ).getOrHandle {
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
                    hentPerson().getOrHandle {
                        throw IllegalStateException("Kunne ikke lukke søknad ${lukkSøknadCommand.søknadId} og søknadsbehandling. Underliggende grunn: $it")
                    }
                },
                clock = clock,
                hentSaksbehandlerNavn = {
                    hentSaksbehandlerNavn(it).getOrHandle {
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
            lagBrevRequest.tap {
                require(it.saksnummer == sak.saksnummer)
            }
            require(sak.hentSøknad(søknad.id).orNull()!! == søknad)
            søknadsbehandling?.also {
                require(sak.søknadsbehandlinger.contains(søknadsbehandling))
                require(søknadsbehandling.søknad == søknad)
            }
        }

        object IkkeLagBrevRequest
    }

    object IngenÅpneRevurderinger
    object FantIkkeSøknadsbehandlingForSøknad

    sealed class KunneIkkeOppdatereStønadsperiode {
        object FantIkkeBehandling : KunneIkkeOppdatereStønadsperiode()
        object StønadsperiodeOverlapperMedLøpendeStønadsperiode : KunneIkkeOppdatereStønadsperiode()
        object StønadsperiodeForSenerePeriodeEksisterer : KunneIkkeOppdatereStønadsperiode()
        data class KunneIkkeOppdatereGrunnlagsdata(val feil: no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeOppdatereStønadsperiode) :
            KunneIkkeOppdatereStønadsperiode()

        data class KunneIkkeHenteGjeldendeVedtaksdata(val feil: Sak.KunneIkkeHenteGjeldendeVedtaksdata) :
            KunneIkkeOppdatereStønadsperiode()

        object StønadsperiodeInneholderAvkortingPgaUtenlandsopphold : KunneIkkeOppdatereStønadsperiode()
    }

    fun avventerKravgrunnlag(): Boolean {
        return revurderinger.filterIsInstance<IverksattRevurdering>()
            .any { it.tilbakekrevingsbehandling.avventerKravgrunnlag() }
    }
}

object IngenÅpneReguleringer

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
