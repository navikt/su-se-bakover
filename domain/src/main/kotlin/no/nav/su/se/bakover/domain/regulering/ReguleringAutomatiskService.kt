package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak

sealed interface BleIkkeRegulert {
    val saksnummer: Saksnummer

    data class FantIkkeSak(
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    data class HarÅpenReguleringFraFør(
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    // TODO erstatt med ForsøkerIkkeRegulere
    data class SkalIkkeRegulere(
        val feil: Sak.KanIkkeRegulere,
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    data class IkkeLøpendeSak(
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    // TODO Ta i bruk denne..
    data class AlleredeRegulert(
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    data class FinnesÅpenRegulering(
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    /*
     * Kan bety regulering ikke er gjennomførbart med reguleringsbehandling pga vedtaksperioder som ikke støttes,
     * eller at behandling medfører flere endringer enn nytt grunnbeløp og må ha vanlig behandling med vedtaksbrev.
     */
    data class MåRegulereMedRevurdering(
        override val saksnummer: Saksnummer,
        val feil: Sak.KanIkkeRegulere.MåRevurdere,
    ) : BleIkkeRegulert

    data class UthentingFradragPesysFeilet(
        val feil: HentingAvEksterneReguleringerFeiletForBruker,
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    data class KunneOppretteRegulering(
        val feil: Sak.KanIkkeRegulere,
        override val saksnummer: Saksnummer,
        val tidsbrukSekunder: Int,
    ) : BleIkkeRegulert

    data class KunneIkkeBehandleAutomatisk(
        val feil: KunneIkkeBehandleRegulering,
        override val saksnummer: Saksnummer,
        val tidsbrukSekunder: Int,
    ) : BleIkkeRegulert

    data class UkjentFeil(
        val feil: Throwable,
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert
}

fun BleIkkeRegulert.toResultat(
    utfall: Reguleringsresultat.Utfall,
    beskrivelse: String = "",
    tidsbrukSekunder: Int? = null,
) = Reguleringsresultat(
    saksnummer = saksnummer,
    utfall = utfall,
    beskrivelse = beskrivelse,
    tidsbrukSekunder = tidsbrukSekunder,
)

interface ReguleringAutomatiskService {
    fun startAutomatiskRegulering(
        fraOgMedMåned: Måned,
    ): List<Either<BleIkkeRegulert, ReguleringOppsummering>>

    fun startAutomatiskReguleringForInnsyn(
        command: StartAutomatiskReguleringForInnsynCommand,
    )
}
