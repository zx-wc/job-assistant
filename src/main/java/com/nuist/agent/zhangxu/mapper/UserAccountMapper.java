package com.nuist.agent.zhangxu.mapper;

import com.nuist.agent.zhangxu.entity.UserAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// 用户账号Mapper 供登录注册功能从数据库读写用户账号
public interface UserAccountMapper {

    // 根据用户名查询账号 用于注册查重和登录校验
    @Select("""
            select id, username, password, nickname, create_time
            from t_user_account
            where username = #{username}
            """)
    UserAccount selectByUsername(@Param("username") String username);

    // 新增用户账号 主键回填到 id
    @Insert("""
            insert into t_user_account(username, password, nickname)
            values(#{username}, #{password}, #{nickname})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserAccount account);
}
