package com.ct.ai.agent.mapper;


import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import com.ct.ai.agent.dao.ChatMessage;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageRepository extends CrudRepository<ChatMessageMapper, ChatMessage> {
}