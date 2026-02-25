package com.ktb.community.repository;

import com.ktb.community.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {
    List<Image> findByPostIdAndDeletedAtIsNull(Long postId);

    List<Image> findByPostIdAndDeletedAtIsNullOrderByDisplayOrderAsc(Long postId);
}
