package com.telme.chat.service;

public interface ChatProcessingPort {

    void request(ChatProcessingCommand command);
}
