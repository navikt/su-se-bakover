package no.nav.su.se.bakover.domain.brev.command

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype

sealed interface IverksettSøknadsbehandlingDokumentCommand : GenererDokumentCommand {
    /**
     * Når vi innvilger en søknad, så sender vi et enkeltvedtak.
     */
    data class Innvilgelse(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        val beregning: Beregning,
        val harEktefelle: Boolean,
        val forventetInntektStørreEnn0: Boolean,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val attestant: NavIdentBruker.Attestant?,
        val fritekst: String,
        val satsoversikt: Satsoversikt,
        val sakstype: Sakstype,
    ) : IverksettSøknadsbehandlingDokumentCommand

    /**
     * Når vi avslår en søknad, så sender vi et enkeltvedtak.
     */
    data class Avslag(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        val avslag: no.nav.su.se.bakover.domain.behandling.avslag.Avslag,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val attestant: NavIdentBruker.Attestant?,
        val fritekst: String,
        val forventetInntektStørreEnn0: Boolean,
        val satsoversikt: Satsoversikt?,
        val sakstype: Sakstype,
    ) : IverksettSøknadsbehandlingDokumentCommand
}
