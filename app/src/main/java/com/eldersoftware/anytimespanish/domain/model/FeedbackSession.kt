package com.eldersoftware.anytimespanish.domain.model

data class FeedbackSession(
    val topicTitle: String,
    val transcript: String,
)

class FeedbackSessionStore {
    private var pending: FeedbackSession? = null

    fun set(session: FeedbackSession) {
        pending = session
    }

    fun consume(): FeedbackSession? = pending.also { pending = null }
}
