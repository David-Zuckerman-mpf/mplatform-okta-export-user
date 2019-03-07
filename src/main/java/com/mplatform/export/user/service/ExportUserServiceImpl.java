package com.mplatform.export.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mplatform.export.user.config.ApplicationProperties;
import com.mplatform.export.user.model.*;
import com.okta.sdk.client.Client;
import com.okta.sdk.impl.resource.application.DefaultAppUserList;
import com.okta.sdk.impl.resource.user.DefaultUserList;
import com.okta.sdk.resource.application.AppUserList;
import com.okta.sdk.resource.user.UserList;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import org.slf4j.Logger;

@Service
public class ExportUserServiceImpl implements ExportUserService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private Client client;

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private Environment environment;

    private final ObjectMapper mapper = new ObjectMapper();

    public void exportToMariaDb()
    {
        try {
            boolean insertDataStore = true;
            List<String> appNames = Arrays.asList("xanadu", "minsights");//, "usermgmt");

            if (truncateTable("app_user") && truncateTable("users")) {
                List<UserDataStore> users = getUsers();

                if(insertUsers(users)) {
                    for (String appName : appNames) {
                        List<AppUserDataStore> appUserDataStores = getAppUserList(appName);
                        if (!(insertAppUsers(appUserDataStores))) {
                            insertDataStore = false;
                        }
                    }
                }
                else
                {
                    insertDataStore = false;
                }

                if (insertDataStore) {
                    boolean ret = insertdatastore();
                }
            }
        }
        catch(URISyntaxException uriEx)
        {
            logger.error(uriEx.getMessage());
        }
    }

    private boolean truncateTable(String tableName)
    {
        boolean retValue = true;
        String sqlTruncate = "truncate " + tableName;
        String dataStoreDbUrl = environment.getProperty("mariaDB.url") + "/" + environment.getProperty("mariaDB.dataStoreDB") + "?rewriteBatchedStatements=true";
        String dbUser = environment.getProperty("mariaDB.user");
        String dbPassword = environment.getProperty("mariaDB.password");
        try (Connection con = DriverManager.getConnection(
                dataStoreDbUrl,
                dbUser,
                dbPassword);
             PreparedStatement psTruncate = con.prepareStatement(sqlTruncate))
        {
            psTruncate.execute();
        }

        catch (SQLException ex)
        {
            logger.error(ex.getMessage());
            retValue = false;
        }

        return retValue;
    }

    private boolean insertdatastore(){
        String agencygeoDB = environment.getProperty("mariaDB.agencygeoDB");
        String sql = "Insert user_data_store\n" +
                "select distinct u.*, a.agency_id, ag.name as agency_name, a.geo_id, g.name as geo_name, g.data_center, a.permission_role\n" +
                "from users u\n" +
                "inner join app_user a\n" +
                "on u.id = a.id\n" +
                "inner join " + agencygeoDB + ".agency ag\n" +
                "on a.agency_id = ag.id\n" +
                "inner join " + agencygeoDB + ".geo g\n" +
                "on a.geo_id = g.id where permission_role is not null";
        String sqlDelete = "delete from user_data_store";
        String sqlUpdateStatus = "update import_status set last_import = CURDATE()";
        String dataStoreDbUrl = environment.getProperty("mariaDB.url") + "/" + environment.getProperty("mariaDB.dataStoreDB") + "?rewriteBatchedStatements=true";
        String dbUser = environment.getProperty("mariaDB.user");
        String dbPassword = environment.getProperty("mariaDB.password");
        boolean retValue = true;

        try (Connection con = DriverManager.getConnection(
                dataStoreDbUrl,
                dbUser,
                dbPassword)) {
            try (PreparedStatement ps = con.prepareStatement(sql);
                 PreparedStatement psDelete = con.prepareStatement(sqlDelete);
                 PreparedStatement psUdateStatus = con.prepareStatement(sqlUpdateStatus))
            {

                    con.setAutoCommit(false);

                    psDelete.executeUpdate();

                    ps.executeUpdate();

                    psUdateStatus.executeUpdate();

                    con.commit();
            } catch (Exception e) {
                logger.error(e.getMessage());
                con.rollback();
                retValue = false;
            }
            finally
            {
                con.setAutoCommit(true);
            }
        }
        catch (SQLException e) {

        }

        return retValue;
    }

    private boolean insertAppUsers(List<AppUserDataStore> appUsers){
        String sql = "INSERT INTO app_user(id,agency_id,geo_id,permission_role,app_name)" +
                " VALUES" + "(?,?,?,?,?)";
        String dataStoreDbUrl = environment.getProperty("mariaDB.url") + "/" + environment.getProperty("mariaDB.dataStoreDB") + "?rewriteBatchedStatements=true";
        String dbUser = environment.getProperty("mariaDB.user");
        String dbPassword = environment.getProperty("mariaDB.password");
        boolean retValue = true;

        try (Connection con = DriverManager.getConnection(
                dataStoreDbUrl,
                dbUser,
                dbPassword)) {
            try (PreparedStatement ps = con.prepareStatement(sql);
                 ) {

                con.setAutoCommit(false);
                long recCount = 0;
                for (AppUserDataStore appuser : appUsers) {
                    ps.setString(1, appuser.getUserId());
                    ps.setLong(2, appuser.getAgencyId());
                    ps.setLong(3, appuser.getGeoId());
                    ps.setString(4, appuser.getRole());
                    ps.setObject(5, appuser.getAppName());
                    ps.addBatch();
                    recCount = recCount + 1;
                    if (recCount == 1000) //limit each commit to 1000 records
                    {
                        ps.executeBatch();
                        con.commit();
                        recCount = 0;
                        ps.clearBatch();
                    }
                }
                if (recCount > 0) {
                    int[] count = ps.executeBatch();
                    con.commit();
                }

            } catch (SQLException e) {
                logger.error(e.getMessage());
                con.rollback();
                retValue = false;
            }
            finally {
                con.setAutoCommit(true);
            }
        }
        catch (SQLException e){
            retValue = false;
        }

        return retValue;
    }

    private List<UserDataStore> getUsers() throws URISyntaxException {
        final String USER_URI_PATH = "/api/v1/users?limit=200";
        String nextPageLink = null;
        List<UserDataStore> users = new ArrayList<>();
        SimpleDateFormat sourceFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        boolean firstTime = true;

        while(nextPageLink != null || firstTime) {
            UserList userList;
            if (firstTime) {
                UriComponentsBuilder servletUriComponentsBuilder = createBuilder(applicationProperties.getOrgUrl() + USER_URI_PATH);
                userList = client.getDataStore().getResource(servletUriComponentsBuilder.build().toUriString(), UserList.class);
                firstTime = false;
            }
            else
            {
                userList = client.getDataStore().getResource(nextPageLink, UserList.class);
            }

            DefaultUserList defaultUserList = (DefaultUserList) userList;

            nextPageLink = (String) defaultUserList.getProperty("nextPage");
            ArrayList<LinkedHashMap> userArrayList = ((ArrayList<LinkedHashMap>) defaultUserList.getProperty("items"));

            for (LinkedHashMap userLinkedHashMap : userArrayList) {
                LinkedHashMap profLinkedHashMap = (LinkedHashMap) userLinkedHashMap.get("profile");
                if (profLinkedHashMap != null) {
                    UserDataStore userDataStore = new UserDataStore();
                    userDataStore.setUserId(userLinkedHashMap.get("id").toString());
                    userDataStore.setFirstName(profLinkedHashMap.get("firstName").toString());
                    userDataStore.setLastName(profLinkedHashMap.get("lastName").toString());
                    if (userLinkedHashMap.get("lastLogin") != null) {
                        try {
                            userDataStore.setLastLogin(sourceFormat.parse(userLinkedHashMap.get("lastLogin").toString()));
                        }
                        catch (ParseException e)
                        {
                            logger.error(e.getMessage());
                        }
                    }
                    userDataStore.setLoginEmail(profLinkedHashMap.get("email").toString());
                    users.add(userDataStore);
                }
            }
        }
        return users;
    }
    private UriComponentsBuilder createBuilder(String stringUri) throws URISyntaxException {
        return ServletUriComponentsBuilder.fromUri(new URI(stringUri));
    }


    private boolean insertUsers(List<UserDataStore> users){
        String sql = "INSERT INTO users(id,first_name,last_name,login_email,last_login)" +
                " VALUES" + "(?,?,?,?,?)";
        String dataStoreDbUrl = environment.getProperty("mariaDB.url") + "/" + environment.getProperty("mariaDB.dataStoreDB") + "?rewriteBatchedStatements=true";
        String dbUser = environment.getProperty("mariaDB.user");
        String dbPassword = environment.getProperty("mariaDB.password");
        boolean retValue = true;

            try (Connection con = DriverManager.getConnection(
                    dataStoreDbUrl,
                    dbUser,
                    dbPassword);
                 PreparedStatement ps = con.prepareStatement(sql)
            )
            {
                con.setAutoCommit(false);
                long recCount = 0;
                for (UserDataStore user: users)
                {
                    ps.setString(1,user.getUserId());
                    ps.setString(2,user.getFirstName());
                    ps.setString(3,user.getLastName());
                    ps.setString(4,user.getLoginEmail());
                    ps.setObject(5,user.getLastLogin());
                    ps.addBatch();
                    recCount = recCount + 1;
                    if (recCount == 1000) //limit each commit to 1000 records
                    {
                        ps.executeBatch();
                        con.commit();
                        recCount = 0;
                        ps.clearBatch();
                    }
                }
               if (recCount > 0)
               {
                   ps.executeBatch();
                   con.commit();
               }

            }
            catch (SQLException e)
            {
                logger.error(e.getMessage());
                retValue = false;
            }

            return retValue;
        }

    private List<AppUserDataStore> getAppUserList(String appName) throws URISyntaxException{

        String appId = getApplicationId(appName);
        final String USER_URI_PATH = String.format("/api/v1/apps/%s/users?limit=200", appId);
        AppUserList userList;
        List<AppUserDataStore> appUserDataStores = new ArrayList<>();

        String nextPageLink = null;
        boolean firstTime = true;

        while(nextPageLink != null || firstTime) {
            if(firstTime) {
                UriComponentsBuilder servletUriComponentsBuilder = createBuilder(applicationProperties.getOrgUrl() + USER_URI_PATH);
                userList = client.getDataStore().getResource(servletUriComponentsBuilder.build().toUriString(), AppUserList.class);
                firstTime = false;
            }
            else
            {
                userList = client.getDataStore().getResource(nextPageLink, AppUserList.class);
            }


            if (userList != null) {
                DefaultAppUserList defaultAppUserList = (DefaultAppUserList) userList;

                nextPageLink = (String) defaultAppUserList.getProperty("nextPage");

                ArrayList<LinkedHashMap> userArrayList = ((ArrayList<LinkedHashMap>) defaultAppUserList.getProperty("items"));

                for (LinkedHashMap userLinkedHashMap : userArrayList) {
                    if(userLinkedHashMap.get("id").toString().compareTo("00ul9ym2jydFAMXWb0x7") == 0)
                    {
                        System.out.println("test");
                    }
                    LinkedHashMap profLinkedHashMap = (LinkedHashMap) userLinkedHashMap.get("profile");
                    if (profLinkedHashMap != null) {
                        String privileges = (String) profLinkedHashMap.get("privileges");
                        try {
                            if (privileges != null) {
                                List<Privilege> privilegeList = readPrivilegesValue(privileges);
                                for (Privilege privilege : privilegeList) {
                                    AppUserDataStore appUserDataStore = new AppUserDataStore();
                                    appUserDataStore.setAppName(appName);
                                    appUserDataStore.setUserId(userLinkedHashMap.get("id").toString());
                                    appUserDataStore.setAgencyId(privilege.getAgencyId());
                                    appUserDataStore.setGeoId(privilege.getGeoId());
                                    appUserDataStore.setRole(privilege.getRole());
                                    appUserDataStores.add(appUserDataStore);
                                }
                            }
                        } catch (IOException ex) {
                            logger.error(ex.getMessage());
                        }
                    }
                }
            }
        }
        return appUserDataStores;
    }

    private List<Privilege> readPrivilegesValue(String privilegesString) throws IOException {
        return mapper.readValue(privilegesString, new TypeReference<List<Privilege>>() {});
    }

    private String getApplicationId(String applicationName){

        String applicationId = applicationProperties.getAppId(applicationName);

        if(applicationId == null){
            throw new IllegalArgumentException(String.format("Application %s does not exist.", applicationName));
        }

        return applicationId;

    }

}
