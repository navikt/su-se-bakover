package no.nav.su.se.bakover.domain.brev.command

import beregning.domain.Beregning
import dokument.domain.generer.Brevmal
import dokument.domain.generer.BrevmalCommand
import dokument.domain.generer.Felles
import dokument.domain.generer.PdfInnhold
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.jsonRequest.avslåttSøknadsbehandlingBrev
import no.nav.su.se.bakover.domain.brev.jsonRequest.innvilgetSøknadsbehandlingBrev

sealed interface IverksettSøknadsbehandlingDokumentCommand : BrevmalCommand {
    /**
     * Når vi innvilger en søknad, så sender vi et enkeltvedtak.
     */
    data class Innvilgelse(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        override val sakstype: Sakstype,
        val beregning: Beregning,
        val harEktefelle: Boolean,
        val forventetInntektStørreEnn0: Boolean,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attestant: NavIdentBruker.Attestant?,
        val satsoversikt: Satsoversikt,
        val fritekst: String,
    ) : IverksettSøknadsbehandlingDokumentCommand {
        override val mal: Brevmal<Innvilgelse> get() = innvilgetSøknadsbehandlingBrev
        override fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, this)
    }

    /**
     * Når vi avslår en søknad, så sender vi et enkeltvedtak.
     */
    data class Avslag(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        override val sakstype: Sakstype,
        val avslag: behandling.søknadsbehandling.domain.avslag.Avslag,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attestant: NavIdentBruker.Attestant?,
        // Kun relevant for uføre
        val forventetInntektStørreEnn0: Boolean?,
        val satsoversikt: Satsoversikt?,
        val fritekst: String,
    ) : IverksettSøknadsbehandlingDokumentCommand {
        override val mal: Brevmal<Avslag> get() = avslåttSøknadsbehandlingBrev
        override fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, this)
    }
}
