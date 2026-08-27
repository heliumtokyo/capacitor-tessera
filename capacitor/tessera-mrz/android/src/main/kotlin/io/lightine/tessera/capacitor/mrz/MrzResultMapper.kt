package io.lightine.tessera.capacitor.mrz

import io.lightine.tessera.mrz.model.MrvA
import io.lightine.tessera.mrz.model.MrvB
import io.lightine.tessera.mrz.model.MrzDocument
import io.lightine.tessera.mrz.model.TD1
import io.lightine.tessera.mrz.model.TD2
import io.lightine.tessera.mrz.model.TD3
import io.lightine.tessera.mrz.parsing.ParseResult
import io.lightine.tessera.types.errors.MrzCheckDigitMismatch
import io.lightine.tessera.types.errors.MrzDateNotInCalendar
import io.lightine.tessera.types.errors.MrzValidationError
import io.lightine.tessera.types.vocabulary.MrzField
import org.json.JSONArray
import org.json.JSONObject

internal data class MappedDocument(
    val format: String,
    val documentCode: String,
    val issuingState: String,
    val documentNumber: String,
    val primaryIdentifier: String,
    val secondaryIdentifiers: List<String>,
    val nationality: String,
    val dateOfBirth: String?,
    val sex: String?,
    val dateOfExpiry: String?,
    val optionalData: String?,
)

internal data class MappedValidation(
    val structurallyValid: Boolean,
    val documentNumberCheckDigit: Boolean?,
    val dateOfBirthCheckDigit: Boolean?,
    val dateOfExpiryCheckDigit: Boolean?,
    val optionalDataCheckDigit: Boolean?,
    val compositeCheckDigit: Boolean?,
)

internal data class MappedValidationFailure(
    val code: String,
    val field: String?,
)

internal data class MappedScanResult(
    val status: String,
    val document: MappedDocument,
    val validation: MappedValidation,
    val validationFailures: List<MappedValidationFailure>,
    val rawLines: List<String>,
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("status", status)
            put("document", document.toJson())
            put("validation", validation.toJson())
            if (status == "partial") {
                put("validationFailures", JSONArray(validationFailures.map { it.toJson() }))
            }
            put("raw", JSONObject().put("lines", JSONArray(rawLines)))
        }
}

internal object MrzResultMapper {
    fun map(parse: ParseResult): MappedScanResult? {
        val document =
            when (parse) {
                is ParseResult.Success -> parse.document
                is ParseResult.PartialSuccess -> parse.document
                is ParseResult.Failure -> return null
            }
        val failures = parse.metadata.validationFailures
        return MappedScanResult(
            status = if (parse is ParseResult.Success) "success" else "partial",
            document = mapDocument(document),
            validation = mapValidation(document, failures),
            validationFailures = failures.map(::mapFailure),
            rawLines = document.rawLines,
        )
    }

    fun documentFamily(parse: ParseResult): String? {
        val format =
            when (parse) {
                is ParseResult.Success -> parse.document.format.name
                is ParseResult.PartialSuccess -> parse.document.format.name
                is ParseResult.Failure -> return null
            }
        return when (format) {
            "TD3" -> "passport"
            "TD1", "TD2" -> "id-card"
            "MRV_A", "MRV_B" -> "visa"
            else -> null
        }
    }

    private fun mapDocument(document: MrzDocument): MappedDocument {
        val fields = document.commonFields
        return MappedDocument(
            format = document.format.name.replace("MRV_", "MRV"),
            documentCode = fields.documentType.rawCode,
            issuingState = fields.issuingState.rawCode,
            documentNumber = fields.documentNumber.trimEnd('<'),
            primaryIdentifier = fields.primaryIdentifier,
            secondaryIdentifiers = fields.secondaryIdentifier.split(Regex("\\s+")).filter(String::isNotBlank),
            nationality = fields.nationality.rawCode,
            dateOfBirth = fields.dateOfBirth.computedDate?.toString(),
            sex = fields.rawSex.takeUnless { it == '<' }?.toString(),
            dateOfExpiry = fields.dateOfExpiry.computedDate?.toString(),
            optionalData = optionalData(document)?.trimEnd('<')?.ifBlank { null },
        )
    }

    private fun optionalData(document: MrzDocument): String? =
        when (document) {
            is TD1 -> listOf(document.optionalData1, document.optionalData2).joinToString("")
            is TD2 -> document.optionalData
            is TD3 -> document.personalNumber
            is MrvA -> document.optionalData
            is MrvB -> document.optionalData
        }

    private fun mapValidation(
        document: MrzDocument,
        failures: List<MrzValidationError>,
    ): MappedValidation {
        val mismatches = failures.filterIsInstance<MrzCheckDigitMismatch>().map { it.field }.toSet()
        val checks = document.commonFields.checkDigits
        return MappedValidation(
            structurallyValid = true,
            documentNumberCheckDigit = MrzField.DOCUMENT_NUMBER !in mismatches,
            dateOfBirthCheckDigit = MrzField.DATE_OF_BIRTH !in mismatches,
            dateOfExpiryCheckDigit = MrzField.DATE_OF_EXPIRY !in mismatches,
            optionalDataCheckDigit = checks.optionalData?.let { MrzField.OPTIONAL_DATA !in mismatches },
            compositeCheckDigit = checks.composite?.let { MrzField.COMPOSITE !in mismatches },
        )
    }

    private fun mapFailure(failure: MrzValidationError): MappedValidationFailure =
        MappedValidationFailure(
            code = failure.javaClass.simpleName,
            field =
                when (failure) {
                    is MrzCheckDigitMismatch -> failure.field.toBridgeName()
                    is MrzDateNotInCalendar -> failure.field.toBridgeName()
                    else -> null
                },
        )
}

private fun MrzField.toBridgeName(): String =
    when (this) {
        MrzField.DOCUMENT_TYPE -> "document-type"
        MrzField.ISSUING_STATE -> "issuing-state"
        MrzField.NAME_FIELD -> "name"
        MrzField.DOCUMENT_NUMBER -> "document-number"
        MrzField.NATIONALITY -> "nationality"
        MrzField.DATE_OF_BIRTH -> "date-of-birth"
        MrzField.DATE_OF_EXPIRY -> "date-of-expiry"
        MrzField.OPTIONAL_DATA -> "optional-data"
        MrzField.COMPOSITE -> "composite"
    }

private fun MappedDocument.toJson(): JSONObject =
    JSONObject().apply {
        put("format", format)
        put("documentCode", documentCode)
        put("issuingState", issuingState)
        put("documentNumber", documentNumber)
        put("primaryIdentifier", primaryIdentifier)
        put("secondaryIdentifiers", JSONArray(secondaryIdentifiers))
        put("nationality", nationality)
        put("dateOfBirth", dateOfBirth ?: JSONObject.NULL)
        put("sex", sex ?: JSONObject.NULL)
        put("dateOfExpiry", dateOfExpiry ?: JSONObject.NULL)
        put("optionalData", optionalData ?: JSONObject.NULL)
    }

private fun MappedValidation.toJson(): JSONObject =
    JSONObject().apply {
        put("structurallyValid", structurallyValid)
        put("documentNumberCheckDigit", documentNumberCheckDigit ?: JSONObject.NULL)
        put("dateOfBirthCheckDigit", dateOfBirthCheckDigit ?: JSONObject.NULL)
        put("dateOfExpiryCheckDigit", dateOfExpiryCheckDigit ?: JSONObject.NULL)
        put("optionalDataCheckDigit", optionalDataCheckDigit ?: JSONObject.NULL)
        put("compositeCheckDigit", compositeCheckDigit ?: JSONObject.NULL)
    }

private fun MappedValidationFailure.toJson(): JSONObject =
    JSONObject().apply {
        put("code", code)
        put("field", field ?: JSONObject.NULL)
    }
