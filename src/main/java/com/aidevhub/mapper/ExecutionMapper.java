package com.aidevhub.mapper;

import com.aidevhub.model.Execution;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 执行记录 Mapper
 */
@Mapper
public interface ExecutionMapper extends BaseMapper<Execution> {
}
