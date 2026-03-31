package com.nexusiam.application.service.enricher;

import com.nexusiam.application.dto.response.SSOProfileResponse;
import java.util.List;
import java.util.Map;

public interface ProfileDataEnricherHardcode {
    
    void enrichWithTestData(SSOProfileResponse profile);
    
    List<Map<String, Object>> generateTestRegistrations(String grpId, String email);
}
