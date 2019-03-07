package com.mplatform.export.user.model;

import lombok.Data;


import java.util.Date;


@Data
public class UserDataStore {

    private String userId;

    private String firstName;

    private String lastName;

    private String loginEmail;

    private Date lastLogin;
}
