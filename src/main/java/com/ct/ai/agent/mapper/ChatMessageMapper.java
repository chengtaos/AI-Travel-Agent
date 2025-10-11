package com.ct.ai.agent.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ct.ai.agent.dao.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}

