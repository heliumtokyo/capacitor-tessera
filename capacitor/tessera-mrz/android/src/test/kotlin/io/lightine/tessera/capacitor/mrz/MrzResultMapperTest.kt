package io.lightine.tessera.capacitor.mrz

import io.lightine.tessera.mrz.parsing.MrzParser
import io.lightine.tessera.mrz.parsing.ParseResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MrzResultMapperTest {
    private val specimen =
        listOf(
            "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
            "L898902C36UTO7408122F1204159ZE184226B<<<<<10",
        )

    @Test
    fun mapsSuccessfulTd3WithoutInventingAnUnresolvedCentury() {
        val parse = assertIs<ParseResult.Success>(MrzParser.parseTD3(specimen))

        val mapped = requireNotNull(MrzResultMapper.map(parse))

        assertEquals("success", mapped.status)
        assertEquals("TD3", mapped.document.format)
        assertEquals("passport", MrzResultMapper.documentFamily(parse))
        assertEquals("ERIKSSON", mapped.document.primaryIdentifier)
        assertEquals(listOf("ANNA", "MARIA"), mapped.document.secondaryIdentifiers)
        assertEquals("1974-08-12", mapped.document.dateOfBirth)
        assertNull(mapped.document.dateOfExpiry)
        assertTrue(mapped.validation.documentNumberCheckDigit == true)
        assertTrue(mapped.validation.compositeCheckDigit == true)
    }

    @Test
    fun exposesCheckDigitMismatchAsAPartialObservation() {
        val invalidComposite = specimen.toMutableList().also { it[1] = it[1].dropLast(1) + "1" }
        val parse = assertIs<ParseResult.PartialSuccess>(MrzParser.parseTD3(invalidComposite))

        val mapped = requireNotNull(MrzResultMapper.map(parse))

        assertEquals("partial", mapped.status)
        assertFalse(mapped.validation.compositeCheckDigit ?: true)
        assertTrue(
            mapped.validationFailures.any {
                it.code == "MrzCheckDigitMismatch" && it.field == "composite"
            },
        )
    }

    @Test
    fun returnsNoBridgeResultForStructuralFailure() {
        val parse = assertIs<ParseResult.Failure>(MrzParser.parse("not-an-mrz"))

        assertNull(MrzResultMapper.map(parse))
        assertNull(MrzResultMapper.documentFamily(parse))
    }
}
