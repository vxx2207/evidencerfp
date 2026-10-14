package com.certaaccess.evidencerfp.iam;

import java.time.Instant;
import java.util.List;

public record IamSnapshot(
        Instant capturedAt,
        List<IamAccount> accounts,
        List<ServiceAccount> serviceAccounts
) {
    public IamSnapshot {
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
        serviceAccounts = serviceAccounts == null ? List.of() : List.copyOf(serviceAccounts);
    }
}
