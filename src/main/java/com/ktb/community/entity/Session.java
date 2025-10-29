package com.ktb.community.entity;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RedisHash(value = "session")
public class Session {

    @Id
    private String uuid;

    @Indexed
    private Long userId;
    private String nickname;

    @TimeToLive
    private Long ttl;

}
