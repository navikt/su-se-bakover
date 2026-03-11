package no.nav.su.se.bakover.test.application

import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.DatabaseRepos
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import person.domain.PersonRepo
import person.domain.PersonerOgSakstype

private fun defaultPersonerOgSakstype(
    sakstype: Sakstype,
    fnr: List<Fnr>,
): PersonerOgSakstype = PersonerOgSakstype(sakstype, fnr)

private fun mockedPersonRepo(defaultPersonerOgSakstype: PersonerOgSakstype): PersonRepo = mock {
    on { hentFnrOgSaktypeForSak(any()) } doReturn defaultPersonerOgSakstype
    on { hentFnrForSøknad(any()) } doReturn defaultPersonerOgSakstype
    on { hentFnrForBehandling(any()) } doReturn defaultPersonerOgSakstype
    on { hentFnrForUtbetaling(any()) } doReturn defaultPersonerOgSakstype
    on { hentFnrForRevurdering(any()) } doReturn defaultPersonerOgSakstype
    on { hentFnrForVedtak(any()) } doReturn defaultPersonerOgSakstype
    on { hentFnrForKlage(any()) } doReturn defaultPersonerOgSakstype
}

fun mockedDatabaseRepos(
    defaultSakstype: Sakstype = Sakstype.UFØRE,
    defaultFnr: List<Fnr> = emptyList(),
) = DatabaseRepos(
    avstemming = mock(),
    utbetaling = mock(),
    søknad = mock(),
    sak = mock(),
    person = mockedPersonRepo(defaultPersonerOgSakstype(defaultSakstype, defaultFnr)),
    søknadsbehandling = mock(),
    revurderingRepo = mock(),
    vedtakRepo = mock(),
    personhendelseRepo = mock(),
    dokumentRepo = mock(),
    nøkkeltallRepo = mock(),
    sessionFactory = mock(),
    klageRepo = mock(),
    klageinstanshendelseRepo = mock(),
    reguleringRepo = mock(),
    sendPåminnelseNyStønadsperiodeJobRepo = mock(),
    hendelseRepo = mock(),
    utenlandsoppholdRepo = mock(),
    dokumentSkattRepo = mock(),
    institusjonsoppholdHendelseRepo = mock(),
    oppgaveHendelseRepo = mock(),
    hendelsekonsumenterRepo = mock(),
    dokumentHendelseRepo = mock(),
    stønadStatistikkRepo = mock(),
    sakStatistikkRepo = mock(),
    fritekstRepo = mock(),
    fritekstAvslagRepo = mock(),
    søknadStatistikkRepo = mock(),
    mottakerRepo = mock(),
)
