package com.justdoit.auth.shared;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TurnstileResponse(
    boolean success,
    @JsonProperty("error-codes") List<String> errorCodes,
    String challenge_ts,
    String hostname
) {}