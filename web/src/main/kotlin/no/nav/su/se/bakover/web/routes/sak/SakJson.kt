package no.nav.su.se.bakover.web.routes.sak

import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.domain.KanStansesEllerGjenopptas
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.RegistrerteUtenlandsoppholdJson
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.RegistrerteUtenlandsoppholdJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.klage.KlageJson
import no.nav.su.se.bakover.web.routes.klage.toJson
import no.nav.su.se.bakover.web.routes.regulering.ReguleringJson
import no.nav.su.se.bakover.web.routes.regulering.toJson
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingJson
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadJson
import no.nav.su.se.bakover.web.routes.søknad.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.UtbetalingJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import no.nav.su.se.bakover.web.routes.vedtak.VedtakJson
import no.nav.su.se.bakover.web.routes.vedtak.VedtakPåTidslinjeJson
import no.nav.su.se.bakover.web.routes.vedtak.toJson
import java.time.Clock

internal data class SakJson(
    val id: String,
    val saksnummer: Long,
    val fnr: String,
    val søknader: List<SøknadJson>,
    val behandlinger: List<BehandlingJson>,
    val utbetalinger: List<UtbetalingJson>,
    val utbetalingerKanStansesEllerGjenopptas: KanStansesEllerGjenopptas,
    val revurderinger: List<RevurderingJson>,
    val vedtak: List<VedtakJson>,
    val klager: List<KlageJson>,
    val reguleringer: List<ReguleringJson>,
    val sakstype: String,
    val vedtakPåTidslinje: List<VedtakPåTidslinjeJson>,
    val utenlandsopphold: RegistrerteUtenlandsoppholdJson,
    val versjon: Long,
) {
    companion object {
        internal fun Sak.toJson(clock: Clock, satsFactory: SatsFactory) = SakJson(
            id = id.toString(),
            saksnummer = saksnummer.nummer,
            fnr = fnr.toString(),
            søknader = søknader.map { it.toJson() },
            behandlinger = søknadsbehandlinger.map { it.toJson(satsFactory) },
            utbetalinger = utbetalingstidslinje()?.let {
                it.map {
                    UtbetalingJson(
                        fraOgMed = it.periode.fraOgMed,
                        tilOgMed = it.periode.tilOgMed,
                        beløp = it.beløp,
                        type = when (it) {
                            is UtbetalingslinjePåTidslinje.Ny -> "NY"
                            is UtbetalingslinjePåTidslinje.Opphør -> "OPPHØR"
                            is UtbetalingslinjePåTidslinje.Reaktivering -> "GJENOPPTA"
                            is UtbetalingslinjePåTidslinje.Stans -> "STANS"
                        },
                    )
                }
            } ?: emptyList(),
            utbetalingerKanStansesEllerGjenopptas = kanUtbetalingerStansesEllerGjenopptas(clock),
            revurderinger = revurderinger.map { it.toJson(satsFactory) },
            vedtak = vedtakListe.map { it.toJson() },
            klager = klager.map { it.toJson() },
            reguleringer = reguleringer.map {
                it.toJson(satsFactory)
            },
            sakstype = type.toJson(),
            vedtakPåTidslinje = this.vedtakstidslinje().tidslinje.toJson(),
            utenlandsopphold = this.utenlandsopphold.toJson(),
            versjon = this.versjon.value,
        )
    }
}

enum class SakstypeJson {
    ALDER,
    UFØRE,
    ;
}

internal fun Sakstype.toJson(): String {
    return when (this) {
        Sakstype.ALDER -> SakstypeJson.ALDER.toString().lowercase()
        Sakstype.UFØRE -> SakstypeJson.UFØRE.toString().lowercase()
    }
}

internal data class AlleredeGjeldendeSakForBrukerJson(
    val uføre: BegrensetSakinfoJson,
    val alder: BegrensetSakinfoJson,
)

internal data class BegrensetSakinfoJson(
    val harÅpenSøknad: Boolean,
    val iverksattInnvilgetStønadsperiode: PeriodeJson?,
)
