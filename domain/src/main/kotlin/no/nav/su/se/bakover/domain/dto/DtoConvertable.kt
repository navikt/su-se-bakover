package no.nav.su.se.bakover.domain.dto

interface DtoConvertable<T> {
    fun toDto(): T
}
