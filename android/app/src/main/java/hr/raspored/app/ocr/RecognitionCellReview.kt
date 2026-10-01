package hr.raspored.app.ocr

/**
 * Review state for one recognized schedule cell.
 *
 * Confidence remains nullable because the current local OCR and AI transports
 * do not yet expose a calibrated probability. The UI must not invent one.
 */
data class RecognitionCellReview(
    val employeeRow: Int?,
    val employeeName: String,
    val day: Int,
    val localCode: String?,
    val aiCode: String?,
    val selectedCode: String?,
    val source: String,
    val confidence: Float?,
    val conflict: Boolean,
    val manuallyConfirmed: Boolean
)
