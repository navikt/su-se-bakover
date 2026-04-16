package no.nav.su.se.bakover.web.services.fradragssjekken

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal class FradragssjekkOppsummeringTest {

    @Test
    fun `oppsummerer opprettede oppgaver per sakstype og fradragstype`() {
        val brukerFradrag = FradragstypeData(Fradragstype.Kategori.Alderspensjon)
        val epsFradrag = FradragstypeData(Fradragstype.Kategori.Arbeidsavklaringspenger)
        val kjoring = FradragssjekkKjøring(
            id = UUID.randomUUID(),
            dato = LocalDate.parse("2026-01-15"),
            dryRun = false,
            status = FradragssjekkKjøringStatus.FULLFØRT,
            opprettet = Instant.parse("2026-01-15T08:00:00Z"),
            ferdigstilt = Instant.parse("2026-01-15T08:05:00Z"),
            resultat = FradragssjekkResultat(
                saksresultater = listOf(
                    opprettetSakResultat(
                        sakId = UUID.randomUUID(),
                        oppgaveAvvik = listOf(
                            oppgaveAvvik(
                                fradragstype = brukerFradrag,
                            ),
                        ),
                    ),
                    opprettetSakResultat(
                        sakId = UUID.randomUUID(),
                        oppgaveAvvik = listOf(
                            oppgaveAvvik(
                                fradragstype = brukerFradrag,
                            ),
                            oppgaveAvvik(
                                fradragstype = epsFradrag,
                            ),
                        ),
                    ),
                    FradragssjekkSakResultat(
                        sakId = UUID.randomUUID(),
                        status = FradragssjekkSakStatus.OPPGAVE_IKKE_OPPRETTET_DRY_RUN,
                        sjekkplan = sjekkplan(UUID.randomUUID()),
                        oppgaveAvvik = listOf(
                            oppgaveAvvik(
                                fradragstype = epsFradrag,
                            ),
                        ),
                    ),
                ),
            ),
        )

        kjoring.lagOppsummering() shouldBe FradragssjekkOppsummering(
            antallOppgaver = 2,
            oppgaverPerSakstype = listOf(
                FradragssjekkSakstypeStatistikk(
                    sakstype = Sakstype.ALDER,
                    antallOppgaver = 2,
                    oppgaverPerFradrag = listOf(
                        FradragssjekkFradragStatistikk(
                            fradragstype = brukerFradrag.kategori.name,
                            beskrivelse = brukerFradrag.beskrivelse,
                            antallOppgaver = 2,
                        ),
                        FradragssjekkFradragStatistikk(
                            fradragstype = epsFradrag.kategori.name,
                            beskrivelse = epsFradrag.beskrivelse,
                            antallOppgaver = 1,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun opprettetSakResultat(
        sakId: UUID,
        oppgaveAvvik: List<Fradragsfunn.Oppgaveavvik>,
    ) = FradragssjekkSakResultat(
        sakId = sakId,
        status = FradragssjekkSakStatus.OPPGAVE_OPPRETTET,
        sjekkplan = sjekkplan(sakId),
        oppgaveAvvik = oppgaveAvvik,
        opprettetOppgave = OppgaveopprettelseResultat.Opprettet(
            oppgaveId = OppgaveId("12345"),
            sakId = sakId,
        ),
    )

    private fun oppgaveAvvik(
        fradragstype: FradragstypeData,
    ) = Fradragsfunn.Oppgaveavvik(
        kode = OppgaveConfig.Fradragssjekk.AvvikKode.FRADRAG_DIFF_OVER_10_PROSENT,
        oppgavetekst = "Oppgaveavvik",
        fradragstype = fradragstype,
    )

    private fun sjekkplan(
        sakId: UUID,
    ) = SjekkPlanData(
        sak = SakInfo(
            sakId = sakId,
            saksnummer = Saksnummer(2026001),
            fnr = Fnr("12345678901"),
            type = Sakstype.ALDER,
        ),
        sjekkpunkter = emptyList(),
    )
}
