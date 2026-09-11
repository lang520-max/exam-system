package com.caikailang.examsystem.mapper;

import com.caikailang.examsystem.domain.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户持久层。
 *
 * <p>语句都很简单，全部用注解实现，因此<b>没有</b>对应的 UserMapper.xml，
 * 也就不存在「接口声明了方法、XML 里却没有 statement」的隐患。</p>
 *
 * @author lang520-max
 */
public interface UserMapper {

    @Insert("INSERT INTO `user` (username, password, nickname, role, created_at, updated_at) "
        + "VALUES (#{username}, #{password}, #{nickname}, #{role}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    /** 登录校验用：返回的对象带 BCrypt 哈希 */
    @Select("SELECT id, username, password, nickname, role, created_at, updated_at "
        + "FROM `user` WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    @Select("SELECT id, username, password, nickname, role, created_at, updated_at "
        + "FROM `user` WHERE id = #{id}")
    User findById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM `user` WHERE username = #{username}")
    int countByUsername(@Param("username") String username);
}
