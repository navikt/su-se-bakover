@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Søknadsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.common.extensions.zoneIdOslo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.harEPS
import no.nav.su.se.bakover.domain.grunnlag.firstOrThrowIfMultipleOrEmpty
import no.nav.su.se.bakover.domain.grunnlag.harForventetInntektStørreEnn0
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.avslag.ErAvslag
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

sealed interface KanGenerereAvslagsbrev : KanGenerereBrev, ErAvslag {

    override fun lagBrevutkastCommandForSaksbehandler(
        satsFactory: SatsFactory,
        utførtAv: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ) = lagDokumentCommand(
        satsFactory = satsFactory,
        fritekst = fritekst,
        saksbehandler = utførtAv,
        // I noen tilfeller kan den være underkjent og da viser vi attestant fra attesteringen, selvom den kan endres senere.
        attestant = attesteringer.prøvHentSisteAttestering()?.attestant,
    )

    override fun lagBrevutkastCommandForAttestant(
        satsFactory: SatsFactory,
        utførtAv: NavIdentBruker.Attestant,
    ) = lagDokumentCommand(
        satsFactory = satsFactory,
        fritekst = fritekstTilBrev,
        saksbehandler = saksbehandler,
        attestant = utførtAv,
    )

    override fun lagBrevCommand(
        satsFactory: SatsFactory,
    ) = lagDokumentCommand(
        satsFactory = satsFactory,
        fritekst = fritekstTilBrev,
        saksbehandler = saksbehandler,
        attestant = attesteringer.hentSisteIverksatteAttesteringOrNull()!!.attestant,
    )

    private fun lagDokumentCommand(
        satsFactory: SatsFactory,
        saksbehandler: NavIdentBruker.Saksbehandler,
        attestant: NavIdentBruker.Attestant?,
        fritekst: String,
    ): IverksettSøknadsbehandlingDokumentCommand.Avslag {
        val bosituasjon = grunnlagsdata.bosituasjon
        val harEktefelle = grunnlagsdata.bosituasjon.ifNotEmpty { harEPS() } ?: false
        val formuegrunnlag = if (avslagsgrunner.contains(Avslagsgrunn.FORMUE)) {
            vilkårsvurderinger.formue.grunnlag.firstOrThrowIfMultipleOrEmpty()
        } else {
            null
        }

        return IverksettSøknadsbehandlingDokumentCommand.Avslag(
            avslag = Avslag(
                avslagsgrunner = avslagsgrunner,
                harEktefelle = harEktefelle,
                beregning = beregning,
                formuegrunnlag = formuegrunnlag,
                // TODO("håndter_formue egentlig knyttet til formuegrenser")
                halvtGrunnbeløpPerÅr = satsFactory.grunnbeløp(opprettet.toLocalDate(zoneIdOslo))
                    .halvtGrunnbeløpPerÅrAvrundet(),
            ),
            saksbehandler = saksbehandler,
            attestant = attestant,
            // TODO jah: 1) vil ikke lenger fungere for alder. 2) kan kanskje dra ut i en mer generell funksjon.
            forventetInntektStørreEnn0 = vilkårsvurderinger.uføreVilkår()
                .getOrNull()!!.grunnlag.harForventetInntektStørreEnn0(),
            fritekst = fritekst,
            saksnummer = saksnummer,
            fødselsnummer = fnr,
            satsoversikt = Satsoversikt.fra(bosituasjon, satsFactory, sakstype),
            sakstype = sakstype,
        )
    }
}
