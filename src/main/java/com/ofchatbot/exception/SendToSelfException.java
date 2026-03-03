package com.ofchatbot.exception;

/**
 * Thrown when OnlyFans API returns "Cannot send message to yourself" (400).
 * Typically happens when the creator unlocks their own PPV (e.g. testing) and the webhook
 * is processed with a fan record whose chat ID is the creator's. Callers should skip sending
 * and log that the send was skipped, not treat as a failure.
 */
public class SendToSelfException extends RuntimeException {

    private final String chatId;

    public SendToSelfException(String chatId, Throwable cause) {
        super("Cannot send message to yourself (chat: " + chatId + ")", cause);
        this.chatId = chatId;
    }

    public String getChatId() {
        return chatId;
    }
}
