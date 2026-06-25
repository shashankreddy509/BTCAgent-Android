package com.gshashank.btcagent.data.model

/**
 * Domain model for the Morning Briefing screen — MOBILE-9.
 *
 * @param timestampMs Epoch-milliseconds of the briefing generation time, or null when the
 *   server has not generated a briefing yet.
 * @param markdown    The freeform Claude-generated Markdown text.
 */
data class BriefingData(
    val timestampMs: Long?,
    val markdown: String,
) {
    /**
     * True when this represents the "no briefing yet" default state.
     *
     * A briefing is considered empty when the text is blank, or equals the sentinel
     * value "No briefing generated yet." — the canonical signal from the API that no
     * briefing has been generated, regardless of what the timestamp says. The blank
     * guard also covers the server omitting the `text` field entirely (DTO default "").
     * A null timestamp paired with real content is still renderable (isEmpty=false).
     */
    val isEmpty: Boolean
        get() = markdown.isBlank() || markdown == "No briefing generated yet."
}
