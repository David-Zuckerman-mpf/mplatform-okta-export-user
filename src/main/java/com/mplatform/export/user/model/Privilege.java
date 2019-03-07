package com.mplatform.export.user.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Privilege {

    @JsonProperty("agency_id")
    private Long agencyId;

    @JsonProperty("geo_id")
    private Long geoId;

    private String role;
}
