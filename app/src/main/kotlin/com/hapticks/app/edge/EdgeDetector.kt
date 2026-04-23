package com.hapticks.app.edge

enum class Edge { TOP, BOTTOM }

class EdgeDetector(
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
) {

    enum class Decision { FIRE, SKIP }

    @Volatile private var lastEdge: Edge? = null
    @Volatile private var lastFiredAtMs: Long = Long.MIN_VALUE

    @Synchronized
    fun detect(edge: Edge, nowMs: Long): Decision {
        val prev = lastEdge
        val elapsed = nowMs - lastFiredAtMs
        val shouldFire = (prev == null) || (prev != edge) || (elapsed > debounceMs)
        if (!shouldFire) return Decision.SKIP
        lastEdge = edge
        lastFiredAtMs = nowMs
        return Decision.FIRE
    }

    @Synchronized
    internal fun reset() {
        lastEdge = null
        lastFiredAtMs = Long.MIN_VALUE
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS: Long = 500L

        val Shared: EdgeDetector = EdgeDetector()
    }
}
