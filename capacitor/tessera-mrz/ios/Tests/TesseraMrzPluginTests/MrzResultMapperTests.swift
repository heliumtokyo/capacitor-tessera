import Tessera
import XCTest
@testable import TesseraMrzPlugin

final class MrzResultMapperTests: XCTestCase {
    private let specimen = """
    P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<
    L898902C36UTO7408122F1204159ZE184226B<<<<<10
    """
    private let referenceTime = KotlinInstant.companion.fromEpochMilliseconds(
        epochMilliseconds: 1_777_593_600_000
    )

    func testMapsSuccessfulTD3WithoutInventingAnUnresolvedCentury() {
        let parse = MrzParser.shared.parseTD3(input: specimen, referenceTime: referenceTime)
        XCTAssertTrue(parse is ParseResult.Success)

        let mapped = MrzResultMapper.map(parse)

        XCTAssertEqual(mapped?.status, "success")
        XCTAssertEqual(mapped?.document.format, "TD3")
        XCTAssertEqual(MrzResultMapper.documentFamily(parse), "passport")
        XCTAssertEqual(mapped?.document.primaryIdentifier, "ERIKSSON")
        XCTAssertEqual(mapped?.document.secondaryIdentifiers, ["ANNA", "MARIA"])
        XCTAssertEqual(mapped?.document.dateOfBirth, "1974-08-12")
        XCTAssertNil(mapped?.document.dateOfExpiry)
        XCTAssertEqual(mapped?.validation.documentNumberCheckDigit, true)
        XCTAssertEqual(mapped?.validation.compositeCheckDigit, true)
    }

    func testExposesCheckDigitMismatchAsAPartialObservation() {
        let invalidComposite = String(specimen.dropLast()) + "1"
        let parse = MrzParser.shared.parseTD3(input: invalidComposite, referenceTime: referenceTime)
        XCTAssertTrue(parse is ParseResult.PartialSuccess)

        let mapped = MrzResultMapper.map(parse)

        XCTAssertEqual(mapped?.status, "partial")
        XCTAssertEqual(mapped?.validation.compositeCheckDigit, false)
        XCTAssertTrue(
            mapped?.validationFailures.contains {
                $0.code == "MrzCheckDigitMismatch" && $0.field == "composite"
            } == true
        )
    }

    func testMapsDocumentNumberFailureField() {
        var lines = specimen.split(separator: "\n").map(String.init)
        var secondLine = Array(lines[1])
        secondLine[9] = "7"
        lines[1] = String(secondLine)
        let parse = MrzParser.shared.parseTD3(
            input: lines.joined(separator: "\n"),
            referenceTime: referenceTime
        )

        let mapped = MrzResultMapper.map(parse)

        XCTAssertEqual(mapped?.validation.documentNumberCheckDigit, false)
        XCTAssertTrue(
            mapped?.validationFailures.contains {
                $0.code == "MrzCheckDigitMismatch" && $0.field == "document-number"
            } == true
        )
    }

    func testReturnsNoBridgeResultForStructuralFailure() {
        let parse = MrzParser.shared.parse(input: "not-an-mrz", referenceTime: referenceTime)

        XCTAssertTrue(parse is ParseResult.Failure)
        XCTAssertNil(MrzResultMapper.map(parse))
        XCTAssertNil(MrzResultMapper.documentFamily(parse))
    }
}
