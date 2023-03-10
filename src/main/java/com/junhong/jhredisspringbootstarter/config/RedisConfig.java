package com.junhong.jhredisspringbootstarter.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junhong.jhredisspringbootstarter.util.RedisUtils;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhangjunhong
 *
 */
@Configuration
@ConditionalOnClass(RedisUtils.class)
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig {

    @Bean
    @ConditionalOnMissingBean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory lettuceConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(lettuceConnectionFactory);
        //??????Jackson2JsonRedisSerializer???????????????JdkSerializationRedisSerializer???????????????????????????redis???value???
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
//        mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
//        jackson2JsonRedisSerializer.setObjectMapper(mapper);
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        //key??????String??????????????????
        template.setKeySerializer(stringRedisSerializer);
        // hash???key?????????String??????????????????
        template.setHashKeySerializer(stringRedisSerializer);
        // value?????????????????????jackson
        template.setValueSerializer(jackson2JsonRedisSerializer);
        // hash???value?????????????????????jackson
        template.setHashValueSerializer(jackson2JsonRedisSerializer);
        template.afterPropertiesSet();
        //??????redis????????????
        template.setEnableTransactionSupport(true);
        return template;
    }

    @Bean
    @ConditionalOnMissingBean
    public LettuceConnectionFactory lettuceConnectionFactory(RedisProperties properties) {
        // ?????????????????????
        GenericObjectPoolConfig genericObjectPoolConfig = new GenericObjectPoolConfig();
        genericObjectPoolConfig.setMaxIdle(properties.getLettuce().getPool().getMaxIdle());
        genericObjectPoolConfig.setMinIdle(properties.getLettuce().getPool().getMinIdle());
        genericObjectPoolConfig.setMaxTotal(properties.getLettuce().getPool().getMaxActive());
//        genericObjectPoolConfig.setMaxWaitMillis(properties.getLettuce().getPool().getMaxWait().toMillis());

        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .commandTimeout(properties.getTimeout())
                .shutdownTimeout(properties.getLettuce().getShutdownTimeout())
                .poolConfig(genericObjectPoolConfig)
                .build();
        //LettuceConnectionFactory????????????
        LettuceConnectionFactory lettuceConnectionFactory;
        RedisSentinelConfiguration sentinelConfig = getSentinelConfiguration(properties);
        RedisClusterConfiguration clusterConfiguration = getClusterConfiguration(properties);
        if (sentinelConfig != null) {
            lettuceConnectionFactory = new LettuceConnectionFactory(sentinelConfig, clientConfig);
        } else if (clusterConfiguration != null) {
            lettuceConnectionFactory = new LettuceConnectionFactory(clusterConfiguration, clientConfig);
            lettuceConnectionFactory.setDatabase(properties.getDatabase());
        } else {
            RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
            redisStandaloneConfiguration.setDatabase(properties.getDatabase());
            redisStandaloneConfiguration.setHostName(properties.getHost());
            redisStandaloneConfiguration.setPort(properties.getPort());
            redisStandaloneConfiguration.setPassword(RedisPassword.of(properties.getPassword()));
            lettuceConnectionFactory = new LettuceConnectionFactory(redisStandaloneConfiguration, clientConfig);
        }
        return lettuceConnectionFactory;
    }

    @Bean
    public RedisUtils redisUtils(@Qualifier("redisTemplate") RedisTemplate<String,Object> redisTemplate){
        return new RedisUtils(redisTemplate);
    }

    /**
     * ????????????????????????
     * @param properties redis????????????
     * @return ??????????????????
     */
    private RedisClusterConfiguration getClusterConfiguration(RedisProperties properties) {
        RedisProperties.Cluster cluster = properties.getCluster();
        if (cluster != null) {
            RedisClusterConfiguration config = new RedisClusterConfiguration(cluster.getNodes());
            if (properties.getPassword() != null){
                config.setPassword(RedisPassword.of(properties.getPassword()));
            }
            if (cluster.getMaxRedirects() != null) {
                config.setMaxRedirects(cluster.getMaxRedirects());
            }
            return config;
        }
        return null;
    }

    /**
     * ????????????????????????
     * @param properties redis????????????
     * @return ??????????????????
     */
    private RedisSentinelConfiguration getSentinelConfiguration(RedisProperties properties) {
        RedisProperties.Sentinel sentinel = properties.getSentinel();
        if (sentinel != null) {
            RedisSentinelConfiguration config = new RedisSentinelConfiguration();
            config.master(sentinel.getMaster());
            //????????????????????????
            List<RedisNode> nodes = new ArrayList<>();
            for (String node: sentinel.getNodes()) {
                String[] parts = StringUtils.split(node, ":");
                assert parts != null;
                Assert.state(parts.length == 2, "redis??????????????????????????????");
                nodes.add(new RedisNode(parts[0], Integer.parseInt(parts[1])));
            }
            config.setSentinels(nodes);
            if (properties.getPassword() != null){
                config.setPassword(RedisPassword.of(properties.getPassword()));
            }
            config.setDatabase(properties.getDatabase());
            return config;
        }
        return null;
    }
}