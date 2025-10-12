package com.ct.ai.agent.service;

import reactor.core.publisher.Flux;

public interface RagService {

    Flux<String> doChatWithRagQuery(String message, String chatId);

}
