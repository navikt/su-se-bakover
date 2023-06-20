package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import java.time.LocalDate
import java.util.UUID

fun trekkSøknad(
    søknadId: UUID = no.nav.su.se.bakover.test.søknad.søknadId,
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    lukketTidspunkt: Tidspunkt = fixedTidspunkt,
    trukketDato: LocalDate = fixedLocalDate,
) = LukkSøknadCommand.MedBrev.TrekkSøknad(
    søknadId = søknadId,
    saksbehandler = saksbehandler,
    lukketTidspunkt = lukketTidspunkt,
    trukketDato = trukketDato,
)

fun avvisSøknadMedBrev(
    søknadId: UUID = no.nav.su.se.bakover.test.søknad.søknadId,
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    lukketTidspunkt: Tidspunkt = fixedTidspunkt,
    brevvalg: Brevvalg.SaksbehandlersValg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.UtenFritekst(),
) = LukkSøknadCommand.MedBrev.AvvistSøknad(
    søknadId = søknadId,
    saksbehandler = saksbehandler,
    lukketTidspunkt = lukketTidspunkt,
    brevvalg = brevvalg,
)

fun avvisSøknadUtenBrev(
    søknadId: UUID = no.nav.su.se.bakover.test.søknad.søknadId,
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    lukketTidspunkt: Tidspunkt = fixedTidspunkt,
) = LukkSøknadCommand.UtenBrev.AvvistSøknad(
    søknadId = søknadId,
    saksbehandler = saksbehandler,
    lukketTidspunkt = lukketTidspunkt,
)

fun bortfallSøknad(
    søknadId: UUID = no.nav.su.se.bakover.test.søknad.søknadId,
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    lukketTidspunkt: Tidspunkt = fixedTidspunkt,
) = LukkSøknadCommand.UtenBrev.BortfaltSøknad(
    søknadId = søknadId,
    saksbehandler = saksbehandler,
    lukketTidspunkt = lukketTidspunkt,
)
