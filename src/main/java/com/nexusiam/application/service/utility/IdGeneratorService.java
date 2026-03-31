package com.nexusiam.application.service.utility;

import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class IdGeneratorService {

    private final AtomicInteger groupCounter = new AtomicInteger(100000);
    private final AtomicInteger userCounter = new AtomicInteger(900000);

    public String generateGroupId(String oktaProfileId) {
        if (oktaProfileId == null || oktaProfileId.isEmpty()) {
            return generateGroupId();
        }
        int hash = Math.abs(oktaProfileId.hashCode());
        int groupNum = 100000 + (hash % 900000);
        return String.format("GRP-%06d", groupNum);
    }

    public String generateGroupId() {
        return String.format("GRP-%06d", groupCounter.incrementAndGet() % 1000000);
    }

    public String generateUserId(String oktaProfileId) {
        if (oktaProfileId == null || oktaProfileId.isEmpty()) {
            return generateUserId();
        }
        int hash = Math.abs(oktaProfileId.hashCode());
        int userNum = 200000 + (hash % 800000);
        return String.format("USR-%06d", userNum);
    }

    public String generateUserId() {
        return String.format("USR-%06d", userCounter.incrementAndGet() % 1000000);
    }

    public String generateBatteryUnitId(int seed) {
        return String.format("BAT-%04d", Math.abs(seed) % 10000);
    }

    public String generatePlasticUnitId(int seed) {
        return String.format("PL-%04d", Math.abs(seed) % 10000);
    }

    public String generateInternalPortalId(String portalPrefix, int seed) {
        return String.format("INT-%s-%04d", portalPrefix.toUpperCase(), Math.abs(seed) % 10000);
    }
}
