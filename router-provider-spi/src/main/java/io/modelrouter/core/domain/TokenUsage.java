package io.modelrouter.core.domain;

/**
 * Records token usage and cost for an inference call.
 *
 * @param promptTokens     number of tokens in the prompt
 * @param completionTokens number of tokens in the completion
 * @param costUsd          estimated cost in USD
 */
public record TokenUsage(int promptTokens, int completionTokens, double costUsd) {

    /**
     * Returns the total number of tokens (prompt + completion).
     *
     * @return total token count
     */
    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}
