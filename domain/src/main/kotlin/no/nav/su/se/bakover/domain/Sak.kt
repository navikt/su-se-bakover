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
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling.Companion.hentOversendteUtbetalingerUtenFeil
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
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

    fun hentGjeldendeVilkårOgGrunnlag(periode: Periode, clock: Clock): GrunnlagsdataOgVilkårsvurderinger {
        return GjeldendeVedtaksdata(
            periode = periode,
            vedtakListe = NonEmptyList.fromListUnsafe(
                vedtakListe.filterIsInstance<VedtakSomKanRevurderes>().ifEmpty {
                    return GrunnlagsdataOgVilkårsvurderinger.IkkeVurdert
                },
            ),
            clock = clock,
        ).let {
            GrunnlagsdataOgVilkårsvurderinger(it.grunnlagsdata, it.vilkårsvurderinger)
        }
    }

    fun hentGjeldendeBeregningForMåned(månedsperiode: Periode, clock: Clock): Månedsberegning? {
        assert(månedsperiode.getAntallMåneder() == 1)
        return GjeldendeVedtaksdata(
            periode = månedsperiode,
            vedtakListe = NonEmptyList.fromListUnsafe(
                vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
                    .filterNot { it is Vedtak.EndringIYtelse.GjenopptakAvYtelse || it is Vedtak.EndringIYtelse.StansAvYtelse || it is Vedtak.IngenEndringIYtelse }.ifEmpty {
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
        }?.let {
            assert(it.getMånedsberegninger().size == 1)
            it.getMånedsberegninger().first()
        }
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
        )
    }
}

class SakFactory(
    private val uuidFactory: UUIDFactory = UUIDFactory(),
    private val clock: Clock,
) {
    fun nySakMedNySøknad(fnr: Fnr, søknadInnhold: SøknadInnhold): NySak {
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
