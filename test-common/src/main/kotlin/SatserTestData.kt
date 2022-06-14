package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// kommentar jah: Dersom/når satser flyttes ut av kildekoden, f.eks. til databasen, må vi definere satsene for test her. Kan starte med å kopiere [SatsFactoryForSupplerendeStønad]

val satsFactoryTest = SatsFactoryForSupplerendeStønad()

fun satsFactoryTestPåDato(påDato: LocalDate = fixedLocalDate) = satsFactoryTest.gjeldende(påDato = påDato)

fun formuegrenserFactoryTestPåDato(
    påDato: LocalDate = fixedLocalDate,
) = satsFactoryTestPåDato(påDato).formuegrenserFactory

fun formuegrenserFactoryTestPåDato(
    påDato: Tidspunkt = fixedTidspunkt,
    zoneId: ZoneId = ZoneOffset.UTC,
) = satsFactoryTestPåDato(påDato.toLocalDate(zoneId)).formuegrenserFactory
