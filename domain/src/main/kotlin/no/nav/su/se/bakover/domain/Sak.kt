package no.nav.su.se.bakover.domain

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.reduser
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling.Companion.hentOversendteUtbetalingerUtenFeil
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
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

    fun hentGjeldendeVilkårOgGrunnlag(
        periode: Periode,
        clock: Clock,
    ): GrunnlagsdataOgVilkårsvurderinger {
        return hentGjeldendeVedtaksdata(
            periode = periode,
            clock = clock,
        ).fold(
            { GrunnlagsdataOgVilkårsvurderinger.IkkeVurdert },
            {
                GrunnlagsdataOgVilkårsvurderinger(
                    grunnlagsdata = it.grunnlagsdata,
                    vilkårsvurderinger = it.vilkårsvurderinger,
                )
            },
        )
    }

    fun kopierGjeldendeVedtaksdata(
        fraOgMed: LocalDate,
        clock: Clock,
    ): Either<KunneIkkeHenteGjeldendeVedtaksdata, GjeldendeVedtaksdata> {
        return vedtakListe
            .filterIsInstance<VedtakSomKanRevurderes>()
            .ifEmpty { return KunneIkkeHenteGjeldendeVedtaksdata.FantIngenVedtak.left() }
            .let { vedtakSomKanRevurderes ->
                hentGjeldendeVedtaksdata(
                    periode = Periode.create(fraOgMed, vedtakSomKanRevurderes.maxOf { it.periode.tilOgMed }),
                    clock = clock,
                )
            }
    }

    private fun hentGjeldendeVedtaksdata(
        periode: Periode,
        clock: Clock,
    ): Either<KunneIkkeHenteGjeldendeVedtaksdata, GjeldendeVedtaksdata> {
        return vedtakListe
            .filterIsInstance<VedtakSomKanRevurderes>()
            .ifEmpty { return KunneIkkeHenteGjeldendeVedtaksdata.FantIngenVedtak.left() }
            .let { vedtakSomKanRevurderes ->
                GjeldendeVedtaksdata(
                    periode = periode,
                    vedtakListe = NonEmptyList.fromListUnsafe(vedtakSomKanRevurderes),
                    clock = clock,
                ).right()
            }
    }

    sealed class KunneIkkeHenteGjeldendeVedtaksdata {
        object FantIngenVedtak : KunneIkkeHenteGjeldendeVedtaksdata()
    }

    /**
     * Brukes for å hente den seneste gjeldenden/brukte månedsberegningen for en gitt måned i saken.
     *
     * Per nå så er det kun Vedtak i form av [Vedtak.EndringIYtelse] som bidrar til dette, bortsett fra [Vedtak.IngenEndringIYtelse] som har
     * andre beregnings-beløp som ikke skal ha en påverkan på saken.
     * */
    fun hentGjeldendeMånedsberegningForMåned(månedsperiode: Periode, clock: Clock): Månedsberegning? {
        assert(månedsperiode.getAntallMåneder() == 1)
        return GjeldendeVedtaksdata(
            periode = månedsperiode,
            vedtakListe = NonEmptyList.fromListUnsafe(
                vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
                    .filterNot { it is Vedtak.EndringIYtelse.GjenopptakAvYtelse || it is Vedtak.EndringIYtelse.StansAvYtelse || it is Vedtak.IngenEndringIYtelse }
                    .ifEmpty {
                        return null
                    },
            ),
            clock = clock,
        ).gjeldendeVedtakPåDato(månedsperiode.fraOgMed)?.let {
            when (it) {
                is Vedtak.EndringIYtelse.InnvilgetRevurdering -> it.beregning
                is Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling -> it.beregning
                is Vedtak.EndringIYtelse.OpphørtRevurdering -> it.beregning
                is Vedtak.IngenEndringIYtelse -> throw IllegalStateException("Kodefeil: Skal ha filtrert bort Vedtak.EndringIYtelse.IngenEndring")
                is Vedtak.EndringIYtelse.StansAvYtelse -> throw IllegalStateException("Kodefeil: Skal ha filtrert bort Vedtak.EndringIYtelse.StansAvYtelse")
                is Vedtak.EndringIYtelse.GjenopptakAvYtelse -> throw IllegalStateException("Kodefeil: Skal ha filtrert bort Vedtak.EndringIYtelse.GjenopptakAvYtelse")
            }
        }?.let { beregning ->
            beregning.getMånedsberegninger().associateBy { it.periode }[månedsperiode]
        }
    }

    fun hentGjeldendeStønadsperiode(clock: Clock): Periode? =
        hentPerioderMedLøpendeYtelse().filter { it.inneholder(LocalDate.now(clock)) }.maxByOrNull { it.tilOgMed }

    fun harÅpenRevurderingForStansAvYtelse(): Boolean {
        return revurderinger.filterIsInstance<StansAvYtelseRevurdering.SimulertStansAvYtelse>().isNotEmpty()
    }

    fun harÅpenRevurderingForGjenopptakAvYtelse(): Boolean {
        return revurderinger.filterIsInstance<GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse>().isNotEmpty()
    }

    /**
     * Identifiser alle perioder hvor ytelsen har vært eller vil være løpende.
     */
    fun hentPerioderMedLøpendeYtelse(): List<Periode> {
        return vedtakListe.filterIsInstance<Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling>()
            .map { it.periode }
            .flatMap { innvilgetStønadsperiode ->
                vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
                    .lagTidslinje(
                        periode = innvilgetStønadsperiode,
                    ).tidslinje
                    .filterNot { it.originaltVedtak.erOpphør() }
                    .map { it.periode }
                    .reduser()
            }.reduser()
    }
    fun hentÅpneKlager(): List<Klage> = klager.filter { it.erÅpen() }

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
    private fun ingenKommendeOpphørEllerHull(utbetalingslinjer: List<UtbetalingslinjePåTidslinje>, clock: Clock): Boolean {
        val kommendeUtbetalingslinjer = utbetalingslinjer.filter { it.periode.tilOgMed.isAfter(LocalDate.now(clock)) }

        if (kommendeUtbetalingslinjer.any { it is UtbetalingslinjePåTidslinje.Opphør }) {
            return false
        }

        if (kommendeUtbetalingslinjer.map { linje -> linje.periode }.reduser().size > 1) {
            return false
        }

        return true
    }

    fun oppdaterStønadsperiodeForSøknadsbehandling(
        søknadsbehandlingId: UUID,
        stønadsperiode: Stønadsperiode,
        clock: Clock,
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
        ).fold(
            {
                when (it) {
                    KunneIkkeHenteGjeldendeVedtaksdata.FantIngenVedtak -> {
                        // Ignoreres da dette er et gyldig og vanlig case for søknadsbehandling
                    }
                }
            },
            {
                if (it.inneholderOpphørsvedtakMedAvkortingUtenlandsopphold()) {
                    val alleUtbetalingerErOpphørt =
                        utbetalingstidslinje(periode = it.periode).tidslinje.all { utbetalingslinjePåTidslinje ->
                            utbetalingslinjePåTidslinje is UtbetalingslinjePåTidslinje.Opphør
                        }

                    if (!alleUtbetalingerErOpphørt)
                        return KunneIkkeOppdatereStønadsperiode.StønadsperiodeInneholderAvkortingPgaUtenlandsopphold.left()
                }
            },
        )

        return søknadsbehandling.oppdaterStønadsperiode(
            oppdatertStønadsperiode = stønadsperiode,
            clock = clock,
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
