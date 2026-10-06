package jm.yardmoney.receipts

/** Image-quality measurements and the warnings shown to the person before recognition. */
data class ReceiptQuality(val brightness: Double, val sharpness: Double, val warnings: List<String>)

/** Detected paper corners as x,y pairs (top-left, top-right, bottom-right, bottom-left). */
data class ReceiptBoundary(val corners: FloatArray, val confidence: Double) {
    // FloatArray compares by identity; compare contents so equal boundaries are equal.
    override fun equals(other: Any?): Boolean =
        this === other ||
            other is ReceiptBoundary &&
                confidence == other.confidence &&
                corners.contentEquals(other.corners)

    override fun hashCode(): Int = 31 * corners.contentHashCode() + confidence.hashCode()
}
