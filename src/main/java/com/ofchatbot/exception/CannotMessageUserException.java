package com.ofchatbot.exception;

/**
 * Thrown when OnlyFans API returns "Cannot send message to this user" (400).
 * Usually means the fan blocked the creator, restricted DMs, unsubscribed, or the chat is invalid.
 * Callers should stop retrying for this chat/user.
 */
public class CannotMessageUserException extends RuntimeException {

    private final String chatId;

    public CannotMessageUserException(String chatId, Throwable cause) {
        super("Cannot send message to OnlyFans user (chat: " + chatId + "): " + (cause != null ? cause.getMessage() : ""), cause);
        this.chatId = chatId;
    }

    public String getChatId() {
        return chatId;
    }
}
