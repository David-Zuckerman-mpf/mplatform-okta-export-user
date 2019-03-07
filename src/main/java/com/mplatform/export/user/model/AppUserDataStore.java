package com.mplatform.export.user.model;

import lombok.Data;

import java.util.Date;

@Data
public class AppUserDataStore {

    private String appName;

    private String userId;

    private Long agencyId;

    private Long geoId;

    private String role;

}
