package com.aix.storage.mapper;

import com.aix.storage.entity.MessageEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {
}
