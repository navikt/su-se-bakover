package no.nav.su.se.bakover.web.services.fradragssjekken

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.util.UUID

internal class FradragssjekkOppsummeringTest {

    @Test
    fun `oppsummerer opprettede oppgaver per sakstype og fradragstype`() {
        val brukerFradrag = FradragstypeData(Fradragstype.Kategori.Alderspensjon)
        val epsFradrag = FradragstypeData(Fradragstype.Kategori.Arbeidsavklaringspenger)
        val saksresultater = listOf(
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
            FradragssjekkSakResultat.OppgaveIkkeOpprettetDryRun(
                sakId = UUID.randomUUID(),
                sakstype = Sakstype.ALDER,
                sjekkPunkter = emptyList(),
                oppgaveAvvik = listOf(
                    oppgaveAvvik(
                        fradragstype = epsFradrag,
                    ),
                ),
            ),
        )

        lagFradragssjekkOppsummering(saksresultater) shouldBe FradragssjekkOppsummering(
            nøkkeltall = mapOf(
                FradragssjekkSakStatus.OPPGAVE_OPPRETTET to 2,
                FradragssjekkSakStatus.OPPGAVE_IKKE_OPPRETTET_DRY_RUN to 1,
            ),
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
    ) = FradragssjekkSakResultat.OppgaveOpprettet(
        sakId = sakId,
        sakstype = Sakstype.ALDER,
        sjekkPunkter = emptyList(),
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
}
