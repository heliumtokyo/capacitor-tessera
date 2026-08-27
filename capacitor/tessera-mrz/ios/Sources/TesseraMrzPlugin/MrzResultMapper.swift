import Capacitor
import Foundation
import Tessera

struct MappedDocument {
    let format: String
    let documentCode: String
    let issuingState: String
    let documentNumber: String
    let primaryIdentifier: String
    let secondaryIdentifiers: [String]
    let nationality: String
    let dateOfBirth: String?
    let sex: String?
    let dateOfExpiry: String?
    let optionalData: String?

    func toJSObject() -> JSObject {
        [
            "format": format,
            "documentCode": documentCode,
            "issuingState": issuingState,
            "documentNumber": documentNumber,
            "primaryIdentifier": primaryIdentifier,
            "secondaryIdentifiers": secondaryIdentifiers,
            "nationality": nationality,
            "dateOfBirth": jsValue(dateOfBirth),
            "sex": jsValue(sex),
            "dateOfExpiry": jsValue(dateOfExpiry),
            "optionalData": jsValue(optionalData),
        ]
    }
}

struct MappedValidation {
    let structurallyValid: Bool
    let documentNumberCheckDigit: Bool?
    let dateOfBirthCheckDigit: Bool?
    let dateOfExpiryCheckDigit: Bool?
    let optionalDataCheckDigit: Bool?
    let compositeCheckDigit: Bool?

    func toJSObject() -> JSObject {
        [
            "structurallyValid": structurallyValid,
            "documentNumberCheckDigit": jsValue(documentNumberCheckDigit),
            "dateOfBirthCheckDigit": jsValue(dateOfBirthCheckDigit),
            "dateOfExpiryCheckDigit": jsValue(dateOfExpiryCheckDigit),
            "optionalDataCheckDigit": jsValue(optionalDataCheckDigit),
            "compositeCheckDigit": jsValue(compositeCheckDigit),
        ]
    }
}

struct MappedValidationFailure {
    let code: String
    let field: String?

    func toJSObject() -> JSObject {
        ["code": code, "field": jsValue(field)]
    }
}

struct MappedScanResult {
    let status: String
    let document: MappedDocument
    let validation: MappedValidation
    let validationFailures: [MappedValidationFailure]
    let rawLines: [String]

    func toJSObject() -> JSObject {
        var object: JSObject = [
            "status": status,
            "document": document.toJSObject(),
            "validation": validation.toJSObject(),
            "raw": ["lines": rawLines] as JSObject,
        ]
        if status == "partial" {
            object["validationFailures"] = validationFailures.map { $0.toJSObject() }
        }
        return object
    }
}

enum MrzResultMapper {
    static func map(_ parse: ParseResult) -> MappedScanResult? {
        let document: MrzDocument
        let status: String
        if let success = parse as? ParseResult.Success {
            document = success.document
            status = "success"
        } else if let partial = parse as? ParseResult.PartialSuccess {
            document = partial.document
            status = "partial"
        } else {
            return nil
        }

        let failures = parse.metadata.validationFailures
        return MappedScanResult(
            status: status,
            document: mapDocument(document),
            validation: mapValidation(document, failures: failures),
            validationFailures: failures.map(mapFailure),
            rawLines: document.rawLines
        )
    }

    static func documentFamily(_ parse: ParseResult) -> String? {
        let document: MrzDocument
        if let success = parse as? ParseResult.Success {
            document = success.document
        } else if let partial = parse as? ParseResult.PartialSuccess {
            document = partial.document
        } else {
            return nil
        }

        switch document.format.name {
        case "TD3": return "passport"
        case "TD1", "TD2": return "id-card"
        case "MRV_A", "MRV_B": return "visa"
        default: return nil
        }
    }

    private static func mapDocument(_ document: MrzDocument) -> MappedDocument {
        let fields = document.commonFields
        return MappedDocument(
            format: document.format.name.replacingOccurrences(of: "MRV_", with: "MRV"),
            documentCode: fields.documentType as? String ?? "",
            issuingState: fields.issuingState as? String ?? "",
            documentNumber: withoutTrailingFiller(fields.documentNumber),
            primaryIdentifier: fields.primaryIdentifier,
            secondaryIdentifiers: fields.secondaryIdentifier.split(whereSeparator: \.isWhitespace).map(String.init),
            nationality: fields.nationality as? String ?? "",
            dateOfBirth: fields.dateOfBirth.computedDate?.description(),
            sex: character(fields.rawSex).flatMap { $0 == "<" ? nil : $0 },
            dateOfExpiry: fields.dateOfExpiry.computedDate?.description(),
            optionalData: optionalData(document).map(withoutTrailingFiller).flatMap { $0.isEmpty ? nil : $0 }
        )
    }

    private static func optionalData(_ document: MrzDocument) -> String? {
        if let td1 = document as? TD1 { return td1.optionalData1 + td1.optionalData2 }
        if let td2 = document as? TD2 { return td2.optionalData }
        if let td3 = document as? TD3 { return td3.personalNumber }
        if let mrvA = document as? MrvA { return mrvA.optionalData }
        if let mrvB = document as? MrvB { return mrvB.optionalData }
        return nil
    }

    private static func mapValidation(
        _ document: MrzDocument,
        failures: [MrzValidationError]
    ) -> MappedValidation {
        let mismatches = failures.compactMap { ($0 as? MrzCheckDigitMismatch)?.field }
        let checks = document.commonFields.checkDigits
        return MappedValidation(
            structurallyValid: true,
            documentNumberCheckDigit: !contains(mismatches, field: .documentNumber),
            dateOfBirthCheckDigit: !contains(mismatches, field: .dateOfBirth),
            dateOfExpiryCheckDigit: !contains(mismatches, field: .dateOfExpiry),
            optionalDataCheckDigit: checks.optionalData == nil ? nil : !contains(mismatches, field: .optionalData),
            compositeCheckDigit: checks.composite == nil ? nil : !contains(mismatches, field: .composite)
        )
    }

    private static func mapFailure(_ failure: MrzValidationError) -> MappedValidationFailure {
        let field: MrzField?
        if let mismatch = failure as? MrzCheckDigitMismatch {
            field = mismatch.field
        } else if let invalidDate = failure as? MrzDateNotInCalendar {
            field = invalidDate.field
        } else {
            field = nil
        }
        let className = NSStringFromClass(type(of: failure)).split(separator: ".").last.map(String.init)
        let code: String
        if let className, className.hasPrefix("Tessera") {
            code = String(className.dropFirst("Tessera".count))
        } else {
            code = className ?? "MrzValidationError"
        }
        return MappedValidationFailure(
            code: code,
            field: field.map(fieldName)
        )
    }

    private static func contains(_ fields: [MrzField], field: MrzField) -> Bool {
        fields.contains { $0 === field }
    }

    private static func fieldName(_ field: MrzField) -> String {
        if field === MrzField.documentType { return "document-type" }
        if field === MrzField.issuingState { return "issuing-state" }
        if field === MrzField.nameField { return "name" }
        if field === MrzField.documentNumber { return "document-number" }
        if field === MrzField.nationality { return "nationality" }
        if field === MrzField.dateOfBirth { return "date-of-birth" }
        if field === MrzField.dateOfExpiry { return "date-of-expiry" }
        if field === MrzField.optionalData { return "optional-data" }
        return "composite"
    }
}

private func withoutTrailingFiller(_ value: String) -> String {
    String(value.reversed().drop(while: { $0 == "<" }).reversed())
}

private func character(_ value: unichar) -> String? {
    guard let scalar = Unicode.Scalar(value) else { return nil }
    return String(Character(scalar))
}

private func jsValue(_ value: String?) -> JSValue {
    if let value { return value }
    return NSNull()
}

private func jsValue(_ value: Bool?) -> JSValue {
    if let value { return value }
    return NSNull()
}
