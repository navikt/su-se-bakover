package no.nav.su.se.bakover.web.routes.sak

import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.domain.KanStansesEllerGjenopptas
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.RegistrerteUtenlandsoppholdJson
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.RegistrerteUtenlandsoppholdJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.klage.KlageJson
import no.nav.su.se.bakover.web.routes.klage.toJson
import no.nav.su.se.bakover.web.routes.regulering.json.ReguleringJson
import no.nav.su.se.bakover.web.routes.regulering.json.toJson
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingJson
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadJson
import no.nav.su.se.bakover.web.routes.søknad.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SøknadsbehandlingJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.UtbetalingJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import no.nav.su.se.bakover.web.routes.vedtak.VedtakJson
import no.nav.su.se.bakover.web.routes.vedtak.VedtakPåTidslinjeJson
import no.nav.su.se.bakover.web.routes.vedtak.toJson
import tilbakekreving.presentation.api.common.KravgrunnlagJson
import tilbakekreving.presentation.api.common.KravgrunnlagJson.Companion.toJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toJson
import vilkår.formue.domain.FormuegrenserFactory
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import java.time.Clock

internal data class SakJson(
    val id: String,
    val saksnummer: Long,
    val fnr: String,
    val søknader: List<SøknadJson>,
    val behandlinger: List<SøknadsbehandlingJson>,
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
    // TODO jah: Kan denne inneholde historiske tilbakekrevinger utført under revurdering også? Evt. et eget felt for det?
    val tilbakekrevinger: List<TilbakekrevingsbehandlingJson>,
    val uteståendeKravgrunnlag: KravgrunnlagJson?,
) {
    companion object {
        internal fun Sak.toJson(clock: Clock, formuegrenserFactory: FormuegrenserFactory) = SakJson(
            id = id.toString(),
            saksnummer = saksnummer.nummer,
            fnr = fnr.toString(),
            søknader = søknader.map { it.toJson() },
            behandlinger = søknadsbehandlinger.map { it.toJson(formuegrenserFactory) },
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
            revurderinger = revurderinger.map { it.toJson(formuegrenserFactory) },
            vedtak = vedtakListe.map { it.toJson() },
            klager = klager.map { it.toJson() },
            reguleringer = reguleringer.map {
                it.toJson(formuegrenserFactory)
            },
            sakstype = type.toJson(),
            vedtakPåTidslinje = this.vedtakstidslinje()?.toJson() ?: emptyList(),
            utenlandsopphold = this.utenlandsopphold.toJson(),
            versjon = this.versjon.value,
            tilbakekrevinger = this.behandlinger.tilbakekrevinger.toJson(),
            uteståendeKravgrunnlag = this.uteståendeKravgrunnlag?.toJson(),
        )
    }
}

enum class SakstypeJson {
    ALDER,
    UFØRE,
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
