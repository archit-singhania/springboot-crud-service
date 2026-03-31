package com.nexusiam.core.domain.repository;

import com.nexusiam.core.domain.entity.ApiSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiSourceRepository extends JpaRepository<ApiSource, Long> {

    Optional<ApiSource> findByApiPathAndApiMethod(String apiPath, String apiMethod);

    List<ApiSource> findByApiPath(String apiPath);

    List<ApiSource> findByModule(String module);

    List<ApiSource> findByApiMethod(String apiMethod);

    boolean existsByApiPathAndApiMethod(String apiPath, String apiMethod);

    @Query("SELECT a FROM ApiSource a WHERE a.apiPath LIKE %:pathPattern%")
    List<ApiSource> searchByPathPattern(@Param("pathPattern") String pathPattern);

    @Query("SELECT DISTINCT a.apiPath FROM ApiSource a ORDER BY a.apiPath")
    List<String> findAllDistinctApiPaths();

    @Query("SELECT DISTINCT a.module FROM ApiSource a WHERE a.module IS NOT NULL ORDER BY a.module")
    List<String> findAllDistinctModules();

    long countByModule(String module);
}
