package com.appblocker.domain.model

/**
 * Built-in messages shown on the "breaking my plan" screen when the user has
 * not written any of their own. Calm and non-judgemental on purpose: the
 * overlay should feel like a pause, not a scolding.
 */
object DefaultMotivationalMessages {
    val all: List<String> = listOf(
        "Is this how you want to spend the next ten minutes?",
        "The urge usually fades within a minute or two. You don't have to act on it.",
        "You set this limit for a reason. Future you will be glad you kept it.",
        "Nothing in there is more important than what you were about to do.",
        "Boredom is where good ideas start. Give it a moment.",
        "Take three slow breaths, then decide.",
        "You've already done the hard part by pausing.",
        "Scrolling rarely leaves you rested. A short walk might.",
        "What were you doing before you picked up your phone?",
        "Every time you walk away, it gets a little easier.",
        "Your attention is worth protecting.",
        "It will still be there later. This moment won't.",
    )
}
