import Foundation
import LitellmktsKit

/// A bridge error with a localized description.
///
/// The Kotlin side reports only a category, an optional HTTP status and an untranslated technical detail; all
/// user-facing text is produced here, from the string catalog.
struct AppError: LocalizedError {
    let category: ErrorCategory
    let httpStatus: Int?
    let detail: String

    init(_ exception: LitellmktsException) {
        self.init(category: exception.category, httpStatus: exception.httpStatus?.intValue, detail: exception.detail)
    }

    /// Unwraps the Kotlin exception carried by errors thrown from Kotlin `@Throws` functions.
    init(_ error: Error) {
        if let appError = error as? AppError {
            self = appError
        } else if let exception = (error as NSError).kotlinException as? LitellmktsException {
            self.init(exception)
        } else {
            self.init(category: .unknown, httpStatus: nil, detail: error.localizedDescription)
        }
    }

    private init(category: ErrorCategory, httpStatus: Int?, detail: String) {
        self.category = category
        self.httpStatus = httpStatus
        self.detail = detail
    }

    var errorDescription: String? {
        switch category {
        case .http:
            if let httpStatus {
                return String(localized: .errorHttpStatus(httpStatus))
            }
            return String(localized: .errorHttp)
        case .network:
            return String(localized: .errorNetwork)
        case .invalidResponse:
            return String(localized: .errorInvalidResponse)
        case .provider:
            return String(localized: .errorProvider)
        case .configuration:
            return String(localized: .errorConfiguration)
        default:
            return String(localized: .errorUnknown)
        }
    }

    /// Technical detail, shown untranslated below the description.
    var failureReason: String? { detail }
}

private extension NSError {
    var kotlinException: Any? { userInfo["KotlinException"] }
}
