package com.nuist.agent.mapper;

import com.nuist.agent.entity.SensitiveWord;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface SensitiveWordMapper {
    @Insert("""
    insert into t_sensitive_word(word,type) values(#{word},#{type})
""")
    // 开启主键回填 在插入数据之后 自动获取自增长主键设置到参数对象SensitiveWord中
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SensitiveWord sensitiveWord);

    // 根据id删除敏感词
    @Delete("""
    delete from t_sensitive_word where id = #{id}
""")
    int deleteById(@Param("id") Long id);

    // 根据id查询敏感词
    @Select("""
    select id,word,type from t_sensitive_word where id = #{id}
""")
    SensitiveWord selectById(@Param("id") Long id);

    // 根据敏感词类型查询所有敏感词
    @Select("""
    select id,word,type from t_sensitive_word where type = #{type}
""")
    List<SensitiveWord> selectByType(@Param("type") String type);

    //支持模糊查询的分页查询
    @Select("""
        <script>
        select id,word,type from t_sensitive_word
        <where>
             <if test='word!=null and word!=""'>and word like CONCAT('%',#{word},'%')</if>
             <if test='type!=null and type!=""'>and type = #{type}</if>
        </where>
         order by id desc limit #{offset},#{limit}     
        </script>
""")
    List<SensitiveWord> selectByPage(@Param("offset") int offset,
                                     @Param("limit") int limit,
                                     @Param("word") String word,
                                     @Param("type") String type);

    @Select("""
        <script>
        select count(*) from t_sensitive_word
        <where>
             <if test='word!=null and word!=""'>and word like CONCAT('%',#{word},'%')</if>
             <if test='type!=null and type!=""'>and type = #{type}</if>
        </where>
        </script>
""")
    int countByCondition(@Param("word") String word,
                         @Param("type") String type);

    /**
     * 修改敏感词(词内容与类型)
     */
    @Update("update t_sensitive_word set word=#{word},type=#{type} where id=#{id}")
    int update(SensitiveWord sensitiveWord);
}

