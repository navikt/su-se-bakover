package no.nav.su.se.bakover.domain

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørTilOgMedDato
import no.nav.su.se.bakover.common.periode.Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden
import no.nav.su.se.bakover.common.periode.Periode.UgyldigPeriode.TilOgMedDatoMåVæreSisteDagIMåneden
import no.nav.su.se.bakover.common.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling.Companion.hentOversendteUtbetalingerUtenFeil
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.lagTidslinje
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class Saksnummer(@JsonValue val nummer: Long) {
    override fun toString() = nummer.toString()

    init {
        // Since we have a public ctor and json-deserialization directly into the domain object
        if (isInvalid(nummer)) throw IllegalArgumentException(UgyldigSaksnummer.toString())
    }

    companion object {
        fun tryParse(saksnummer: String): Either<UgyldigSaksnummer, Saksnummer> {
            return saksnummer.toLongOrNull()?.let {
                tryParse(it)
            } ?: UgyldigSaksnummer.left()
        }

        private fun tryParse(saksnummer: Long): Either<UgyldigSaksnummer, Saksnummer> {
            if (isInvalid(saksnummer)) return UgyldigSaksnummer.left()
            return Saksnummer(saksnummer).right()
        }

        private fun isInvalid(saksnummer: Long) = saksnummer < 2021
    }

    object UgyldigSaksnummer
}

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
) {
    fun utbetalingstidslinje(
        periode: Periode = Periode.create(
            fraOgMed = LocalDate.MIN,
            tilOgMed = LocalDate.MAX,
        ),
    ): TidslinjeForUtbetalinger {
        val utbetalingslinjer = utbetalinger.hentOversendteUtbetalingerUtenFeil()
            .flatMap { it.utbetalingslinjer }

        return TidslinjeForUtbetalinger(
            periode = periode,
            utbetalingslinjer = utbetalingslinjer,
        )
    }

    fun kopierGjeldendeVedtaksdata(
        fraOgMed: LocalDate,
        clock: Clock,
    ): Either<KunneIkkeHenteGjeldendeVedtaksdata, GjeldendeVedtaksdata> {
        return vedtakListe
            .filterIsInstance<VedtakSomKanRevurderes>()
            .ifEmpty {
                return KunneIkkeHenteGjeldendeVedtaksdata.FinnesIngenVedtakSomKanRevurderes(fraOgMed).left()
            }
            .let { vedtakSomKanRevurderes ->
                val tilOgMed = vedtakSomKanRevurderes.maxOf { it.periode.tilOgMed }
                Periode.tryCreate(fraOgMed, tilOgMed)
                    .mapLeft {
                        when (it) {
                            FraOgMedDatoMåVæreFørTilOgMedDato -> KunneIkkeHenteGjeldendeVedtaksdata.FinnesIngenVedtakSomKanRevurderes(
                                fraOgMed,
                                tilOgMed,
                            )
                            FraOgMedDatoMåVæreFørsteDagIMåneden, TilOgMedDatoMåVæreSisteDagIMåneden,
                            -> KunneIkkeHenteGjeldendeVedtaksdata.UgyldigPeriode(it)
                        }
                    }
                    .flatMap {
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
        return vedtakListe
            .filterIsInstance<VedtakSomKanRevurderes>()
            .ifEmpty { return KunneIkkeHenteGjeldendeVedtaksdata.FinnesIngenVedtakSomKanRevurderes(periode).left() }
            .let { vedtakSomKanRevurderes ->
                GjeldendeVedtaksdata(
                    periode = periode,
                    vedtakListe = NonEmptyList.fromListUnsafe(vedtakSomKanRevurderes),
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
     * Brukes for å hente den seneste gjeldenden/brukte beregningen for en gitt måned i saken.
     *
     * Per nå så er det kun Vedtak i form av [VedtakSomKanRevurderes.EndringIYtelse] som bidrar til dette, bortsett fra [VedtakSomKanRevurderes.IngenEndringIYtelse] som har
     * andre beregnings-beløp som ikke skal ha en påverkan på saken.
     * */
    fun hentGjeldendeBeregningForEndringIYtelse(
        måned: Måned,
        clock: Clock,
    ): Beregning? {
        return GjeldendeVedtaksdata(
            periode = måned,
            vedtakListe = NonEmptyList.fromListUnsafe(
                vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
                    .filterNot { it is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse || it is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse || it is VedtakSomKanRevurderes.IngenEndringIYtelse }
                    .ifEmpty {
                        return null
                    },
            ),
            clock = clock,
        ).gjeldendeVedtakPåDato(måned.fraOgMed)?.let {
            when (it) {
                is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> it.beregning
                is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> it.beregning
                is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> it.beregning
                is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering -> it.beregning
                is VedtakSomKanRevurderes.IngenEndringIYtelse -> throw IllegalStateException("Kodefeil: Skal ha filtrert bort Vedtak.EndringIYtelse.IngenEndring")
                is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse -> throw IllegalStateException("Kodefeil: Skal ha filtrert bort Vedtak.EndringIYtelse.StansAvYtelse")
                is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse -> throw IllegalStateException("Kodefeil: Skal ha filtrert bort Vedtak.EndringIYtelse.GjenopptakAvYtelse")
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

    /**
     * Identifiser alle perioder hvor ytelsen har vært eller vil være løpende.
     */
    fun hentPerioderMedLøpendeYtelse(
        periode: Periode = Periode.create(
            fraOgMed = LocalDate.MIN,
            tilOgMed = LocalDate.MAX,
        ),
    ): List<Periode> {
        return vedtakstidslinje(periode = periode)
            .filterNot { it.erOpphør() }
            .map { it.periode }
            .minsteAntallSammenhengendePerioder()
    }

    fun vedtakstidslinje(
        periode: Periode = Periode.create(
            fraOgMed = LocalDate.MIN,
            tilOgMed = LocalDate.MAX,
        ),
    ): List<VedtakSomKanRevurderes.VedtakPåTidslinje> {
        return vedtakListe.filterIsInstance<VedtakSomKanRevurderes>().lagTidslinje(periode).tidslinje
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
    ): Either<KunneIkkeOppdatereStønadsperiode, Søknadsbehandling.Vilkårsvurdert> {
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

                if (!alleUtbetalingerErOpphørt)
                    return KunneIkkeOppdatereStønadsperiode.StønadsperiodeInneholderAvkortingPgaUtenlandsopphold.left()
            }
        }

        return søknadsbehandling.oppdaterStønadsperiode(
            oppdatertStønadsperiode = stønadsperiode,
            clock = clock,
            formuegrenserFactory = formuegrenserFactory,
        ).mapLeft {
            when (it) {
                is Søknadsbehandling.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata -> {
                    KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
                }
                is Søknadsbehandling.KunneIkkeOppdatereStønadsperiode.UgyldigTilstand -> {
                    KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
                }
            }
        }
    }

    fun ytelseUtløperVedUtløpAv(periode: Periode): Boolean {
        return vedtakstidslinje()
            .lastOrNull()
            ?.let {
                !it.erOpphør() && it.periode slutterSamtidig periode
            } ?: false
    }

    sealed interface KunneIkkeOppretteEllerOppdatereRegulering {
        object FinnesIngenVedtakSomKanRevurderesForValgtPeriode : KunneIkkeOppretteEllerOppdatereRegulering
        object StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig : KunneIkkeOppretteEllerOppdatereRegulering
        object BleIkkeLagetReguleringDaDenneUansettMåRevurderes : KunneIkkeOppretteEllerOppdatereRegulering
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
                    0 -> Triple(UUID.randomUUID(), Tidspunkt.now(clock), startDato)
                    1 -> Triple(r.first().id, r.first().opprettet, minOf(startDato, r.first().periode.fraOgMed))
                    else -> throw IllegalStateException("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende grunn: Det finnes fler enn en åpen regulering.")
                }
            }

        val periode = vedtakstidslinje(
            periode = Periode.create(
                fraOgMed = _startDato,
                tilOgMed = LocalDate.MAX,
            ),
        ).let { tidslinje ->
            tidslinje.filterNot { it.erOpphør() }
                .map { vedtakUtenOpphør -> vedtakUtenOpphør.periode }
                .minsteAntallSammenhengendePerioder()
                .ifEmpty {
                    log.info("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende feil: Har ingen vedtak å regulere fra og med $_startDato")
                    return KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode.left()
                }
        }.also {
            if (it.count() != 1) return KunneIkkeOppretteEllerOppdatereRegulering.StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig.left()
        }.single()

        val gjeldendeVedtaksdata =
            this.hentGjeldendeVedtaksdata(periode = periode, clock = clock)
                .getOrHandle { feil ->
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
        ).mapLeft {
            KunneIkkeOppretteEllerOppdatereRegulering.BleIkkeLagetReguleringDaDenneUansettMåRevurderes
        }
    }

    sealed class KunneIkkeOppdatereStønadsperiode {
        object FantIkkeBehandling : KunneIkkeOppdatereStønadsperiode()
        object StønadsperiodeOverlapperMedLøpendeStønadsperiode : KunneIkkeOppdatereStønadsperiode()
        object StønadsperiodeForSenerePeriodeEksisterer : KunneIkkeOppdatereStønadsperiode()
        data class KunneIkkeOppdatereGrunnlagsdata(val feil: Søknadsbehandling.KunneIkkeOppdatereStønadsperiode) :
            KunneIkkeOppdatereStønadsperiode()

        data class KunneIkkeHenteGjeldendeVedtaksdata(val feil: Sak.KunneIkkeHenteGjeldendeVedtaksdata) :
            KunneIkkeOppdatereStønadsperiode()

        object StønadsperiodeInneholderAvkortingPgaUtenlandsopphold : KunneIkkeOppdatereStønadsperiode()
    }
}

data class NySak(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt,
    val fnr: Fnr,
    val søknad: Søknad.Ny,
) {
    fun toSak(saksnummer: Saksnummer): Sak {
        return Sak(
            id = id,
            saksnummer = saksnummer,
            opprettet = opprettet,
            fnr = fnr,
            søknader = listOf(søknad),
            søknadsbehandlinger = emptyList(),
            utbetalinger = emptyList(),
            revurderinger = emptyList(),
            vedtakListe = emptyList(),
            klager = emptyList(),
        )
    }
}

class SakFactory(
    private val uuidFactory: UUIDFactory = UUIDFactory(),
    private val clock: Clock,
) {
    fun nySakMedNySøknad(
        fnr: Fnr,
        søknadInnhold: SøknadInnhold,
    ): NySak {
        val opprettet = Tidspunkt.now(clock)
        val sakId = uuidFactory.newUUID()
        return NySak(
            id = sakId,
            fnr = fnr,
            opprettet = opprettet,
            søknad = Søknad.Ny(
                id = uuidFactory.newUUID(),
                opprettet = opprettet,
                sakId = sakId,
                søknadInnhold = søknadInnhold,
            ),
        )
    }
}

data class BegrensetSakinfo(
    val harÅpenSøknad: Boolean,
    val iverksattInnvilgetStønadsperiode: Periode?,
)

enum class KanStansesEllerGjenopptas {
    STANS,
    GJENOPPTA,
    INGEN;
}
