package jm.yardmoney.ui

/** A drag that starts below the top never becomes a dismiss gesture mid-scroll. */
internal class FormDismissGate(private val threshold: Float) {
    private var eligible = false
    private var distance = 0f

    fun begin(atTop: Boolean) {
        eligible = atTop
        distance = 0f
    }

    fun pull(delta: Float) {
        if (eligible) distance = (distance + delta).coerceAtLeast(0f)
    }

    fun finish(): Boolean {
        val dismiss = eligible && distance >= threshold
        eligible = false
        distance = 0f
        return dismiss
    }
}
