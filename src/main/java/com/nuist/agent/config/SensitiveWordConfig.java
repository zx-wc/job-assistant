package com.nuist.agent.config;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.allow.WordAllows;
import com.github.houbb.sensitive.word.support.deny.WordDenys;
import com.nuist.agent.entity.SensitiveWord;
import com.nuist.agent.mapper.SensitiveWordMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SensitiveWordConfig {
    private final SensitiveWordMapper sensitiveWordMapper;

    public SensitiveWordConfig(SensitiveWordMapper sensitiveWordMapper) {
        this.sensitiveWordMapper = sensitiveWordMapper;
    }

    // 构建SenstiveWordBs Bean
    // 借助链式配置完成名词设置 需要使用 第三方敏感词词库+本地mysql词库合并为最终词库
    @Bean
    public SensitiveWordBs sensitiveWordBs(){
        return SensitiveWordBs.newInstance()
                // 设置黑名单 黑名单=默认库+mysql本地库中的deny词
                .wordDeny(WordDenys.chains(WordDenys.defaults(),
                        () -> listSenstiveWordByType("deny")
                ))
                // 设置白名单 白名单= 默认库+mysql allow词
                .wordAllow(WordAllows.chains(WordAllows.defaults(),
                        () -> listSenstiveWordByType("allow")
                ))
                .ignoreCase(true) // 忽略大小写
                .ignoreWidth(true) // 忽略全半角
                .enableEmailCheck(true) // 开启邮箱地址检查
                .enableUrlCheck(true) // 开启url检查
                .enableNumCheck(true) // 开启数字检查(拦截手机号/银行卡等隐私信息)
                .numCheckLen(6) // 连续大于等于6位的数字才进行敏感判定
                .init();
    }

    // 从mysql查询指定类型的敏感词
    private List<String> listSenstiveWordByType(String type){
        // 查询指定类型的敏感词详细数据
        List<SensitiveWord> sensitiveWords = sensitiveWordMapper.selectByType(type);
        List<String> wordList = new ArrayList<>();
        // 遍历敏感词对象集合 从其中取出敏感词字符串 存入新集合
        for (SensitiveWord sensitiveWord : sensitiveWords) {
            wordList.add(sensitiveWord.getWord());
        }
        return wordList;
    }
}
