package com.nexusiam.application.service.authorization;

import com.nexusiam.core.domain.entity.UserApiMapping;
import com.nexusiam.core.domain.entity.ApiSource;
import com.nexusiam.core.domain.repository.UserApiMappingRepository;
import com.nexusiam.core.domain.repository.ApiSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private final UserApiMappingRepository mappingRepository;
    private final ApiSourceRepository apiSourceRepository;

    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final int MAX_CACHE_SIZE = 10000;

    private final Map<Long, CachedUserPermissions> permissionCache = new ConcurrentHashMap<>();

    public boolean hasPermission(Long userId, String apiPath, String httpMethod) {

        if (userId == null) {
            log.debug("Null userId provided (SSO user) - skipping permission check");
            return true;
        }

        log.debug("Checking permission for user {} on {} {}", userId, httpMethod, apiPath);

        CachedUserPermissions cached = getOrLoadUserPermissions(userId);

        if (cached == null || cached.permissions.isEmpty()) {
            log.debug("No permissions found for user {}", userId);
            return false;
        }

        String normalizedMethod = httpMethod.toUpperCase();
        boolean hasPermission = cached.permissions.stream()
                .anyMatch(p -> p.matches(apiPath, normalizedMethod));

        log.debug("Permission check for user {} on {} {}: {}",
                userId, httpMethod, apiPath, hasPermission ? "ALLOWED" : "DENIED");

        return hasPermission;
    }

    public List<CachedPermission> getUserPermissions(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        CachedUserPermissions cached = getOrLoadUserPermissions(userId);
        return cached != null ? new ArrayList<>(cached.permissions) : Collections.emptyList();
    }

    public void invalidateUser(Long userId) {
        permissionCache.remove(userId);
        log.info("Invalidated permission cache for user {}", userId);
    }

    public void invalidateUsers(List<Long> userIds) {
        userIds.forEach(this::invalidateUser);
        log.info("Invalidated permission cache for {} users", userIds.size());
    }

    public void clearCache() {
        int size = permissionCache.size();
        permissionCache.clear();
        log.info("Cleared entire permission cache ({} entries)", size);
    }

    public Map<String, Object> getCacheStats() {
        int totalEntries = permissionCache.size();
        long expiredEntries = permissionCache.values().stream()
                .filter(CachedUserPermissions::isExpired)
                .count();

        return Map.of(
                "totalCachedUsers", totalEntries,
                "expiredEntries", expiredEntries,
                "activeEntries", totalEntries - expiredEntries,
                "cacheTTL", CACHE_TTL.toMinutes() + " minutes",
                "maxCacheSize", MAX_CACHE_SIZE
        );
    }

    public void preloadUsers(List<Long> userIds) {
        log.info("Preloading permissions for {} users", userIds.size());
        userIds.forEach(this::getOrLoadUserPermissions);
        log.info("Preload complete");
    }

    private CachedUserPermissions getOrLoadUserPermissions(Long userId) {

        CachedUserPermissions cached = permissionCache.get(userId);

        if (cached != null && !cached.isExpired()) {
            log.trace("Cache HIT for user {}", userId);
            return cached;
        }

        log.debug("Cache MISS for user {} - loading from database", userId);

        if (permissionCache.size() >= MAX_CACHE_SIZE) {
            evictOldestEntries();
        }

        return loadAndCachePermissions(userId);
    }

    private CachedUserPermissions loadAndCachePermissions(Long userId) {
        try {

            List<UserApiMapping> dbMappings =
                    mappingRepository.findUserAccessibleApisWithDetails(userId);

            Map<String, Set<String>> pathToMethodsMap = new HashMap<>();
            Map<String, String> pathToModuleMap = new HashMap<>();

            for (UserApiMapping mapping : dbMappings) {
                ApiSource apiSource = apiSourceRepository.findById(mapping.getApiSourceId()).orElse(null);
                if (apiSource == null) {
                    continue;
                }

                String apiPath = apiSource.getApiPath();
                String apiMethod = apiSource.getApiMethod();
                String module = apiSource.getModule();

                pathToMethodsMap.computeIfAbsent(apiPath, k -> new HashSet<>()).add(apiMethod);
                pathToModuleMap.putIfAbsent(apiPath, module);
            }

            List<CachedPermission> permissions = pathToMethodsMap.entrySet().stream()
                    .map(entry -> new CachedPermission(
                            entry.getKey(),
                            new ArrayList<>(entry.getValue()),
                            pathToModuleMap.get(entry.getKey())
                    ))
                    .collect(Collectors.toList());

            CachedUserPermissions cached = new CachedUserPermissions(permissions);
            permissionCache.put(userId, cached);

            log.debug("Cached {} permissions for user {}", permissions.size(), userId);
            return cached;

        } catch (Exception e) {
            log.error("Failed to load permissions for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private void evictOldestEntries() {
        log.warn("Cache size limit reached, evicting oldest entries");

        List<Map.Entry<Long, CachedUserPermissions>> entries =
                new ArrayList<>(permissionCache.entrySet());

        entries.sort(Comparator.comparing(e -> e.getValue().cachedAt));

        int toRemove = (int) (entries.size() * 0.2);
        for (int i = 0; i < toRemove; i++) {
            permissionCache.remove(entries.get(i).getKey());
        }

        log.info("Evicted {} old cache entries", toRemove);
    }

    private static class CachedUserPermissions {
        final List<CachedPermission> permissions;
        final Instant cachedAt;
        final Instant expiresAt;

        CachedUserPermissions(List<CachedPermission> permissions) {
            this.permissions = permissions;
            this.cachedAt = Instant.now();
            this.expiresAt = cachedAt.plus(CACHE_TTL);
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public static class CachedPermission {
        private final String apiPath;
        private final Set<String> allowedMethods;
        private final String module;

        public CachedPermission(String apiPath, List<String> allowedMethods, String module) {
            this.apiPath = apiPath;
            this.allowedMethods = new HashSet<>(allowedMethods);
            this.module = module;
        }

        public boolean matches(String requestPath, String requestMethod) {
            return this.apiPath.equals(requestPath)
                    && this.allowedMethods.contains(requestMethod.toUpperCase());
        }

        public String getApiPath() { return apiPath; }
        public Set<String> getAllowedMethods() { return allowedMethods; }
        public String getModule() { return module; }
    }
}
