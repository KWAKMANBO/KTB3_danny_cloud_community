package com.ktb.community.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Count {
    @Id
    private Long id;

    @OneToOne
    @MapsId
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(nullable = false)
    private Long likeCount = 0L;

    @Column(nullable = false)
    private Long viewCount = 0L;

    @Column(nullable = false)
    private Long commentCount = 0L;

}
