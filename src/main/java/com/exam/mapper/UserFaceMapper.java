package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.entity.UserFace;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 人脸底片 Mapper
 */
public interface UserFaceMapper extends BaseMapper<UserFace> {

    /**
     * 按用户取底片。user_id 上有唯一键，所以最多一条。
     */
    @Select("SELECT * FROM user_face WHERE user_id = #{userId}")
    UserFace selectByUserId(@Param("userId") Long userId);
}