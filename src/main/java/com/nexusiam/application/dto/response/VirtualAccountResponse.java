package com.nexusiam.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualAccountResponse {
    private String accountNumber;
    private String bankIfsc;
    private String bankName;
    private String bankBranch;
    private String upiHandle;
}
