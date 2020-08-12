/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.apimgt.migration.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.client.sp_migration.APIMStatMigrationException;
import org.wso2.carbon.apimgt.migration.dao.SharedDAO;
import org.wso2.carbon.apimgt.migration.dto.UserRoleFromPermissionDTO;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.apimgt.migration.util.RegistryService;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.carbon.user.core.util.UserCoreUtil;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScopeRoleMappingPopulationClient extends MigrationClientBase implements MigrationClient {
    private static final Log log = LogFactory.getLog(ScopeRoleMappingPopulationClient.class);
    private RegistryService registryService;

    public ScopeRoleMappingPopulationClient(String tenantArguments, String blackListTenantArguments, String tenantRange,
                                            RegistryService registryService, TenantManager tenantManager) throws UserStoreException {
        super(tenantArguments, blackListTenantArguments, tenantRange, tenantManager);
        this.registryService = registryService;
    }

    @Override
    public void databaseMigration() throws APIMigrationException, SQLException {

    }

    @Override
    public void registryResourceMigration() throws APIMigrationException {

    }

    @Override
    public void fileSystemMigration() throws APIMigrationException {

    }

    @Override
    public void cleanOldResources() throws APIMigrationException {

    }

    @Override
    public void statsMigration() throws APIMigrationException, APIMStatMigrationException {

    }

    @Override
    public void tierMigration(List<String> options) throws APIMigrationException {

    }

    @Override
    public void updateArtifacts() throws APIMigrationException {

    }

    @Override
    public void populateSPAPPs() throws APIMigrationException {

    }

    @Override
    public void populateScopeRoleMapping() throws APIMigrationException {
        log.info("Population of Scope-Role Mapping started");
        populateRoleMappingWithUserRoles();
    }

    @Override
    public void updateScopeRoleMappings() throws APIMigrationException {
        log.info("Started Updating Scope-Role Mappings");

        for (Tenant tenant : getTenantsArray()) {
            try {
                registryService.startTenantFlow(tenant);
                if (tenant.getId() != MultitenantConstants.SUPER_TENANT_ID) {
                    APIUtil.loadAndSyncTenantConf(tenant.getId());
                }

                String[] scopesAllowedForCreator = {Constants.API_CREATE_SCOPE, Constants.API_VIEW_SCOPE,
                        Constants.TIER_VIEW_SCOPE, Constants.SUBSCRIPTION_VIEW_SCOPE};
                String[] scopesAllowedForPublisher = {Constants.API_PUBLISH_SCOPE, Constants.API_VIEW_SCOPE,
                        Constants.TIER_VIEW_SCOPE};
                List<String> scopeListAllowedForCreator = Arrays.asList(scopesAllowedForCreator);
                List<String> scopeListAllowedForPublisher = Arrays.asList(scopesAllowedForPublisher);

                // Retrieve the tenant-conf.json of the corresponding tenant
                JSONObject tenantConf = APIUtil.getTenantConfig(tenant.getDomain());
                // Extract the RESTAPIScopes object
                JSONObject restAPIScopes = (JSONObject) tenantConf.get(APIConstants.REST_API_SCOPES_CONFIG);
                if (restAPIScopes != null) {
                    JSONArray scopesArray = (JSONArray) restAPIScopes.get(Constants.SCOPE);
                    for (Object scopeMapping : scopesArray) {
                        JSONObject mapping = (JSONObject) scopeMapping;
                        String scopeName = (String) mapping.get(Constants.NAME);
                        if (scopeListAllowedForCreator.contains(scopeName)) {
                            String roleList = (String) mapping.get(Constants.ROLES);
                            if (!roleList.contains(Constants.CREATOR_ROLE)) {
                                roleList = roleList + ", " + Constants.CREATOR_ROLE;
                                mapping.put(Constants.ROLES, roleList);
                            }
                        }
                        if (scopeListAllowedForPublisher.contains(scopeName)) {
                            String roleList = (String) mapping.get(Constants.ROLES);
                            if (!roleList.contains(Constants.PUBLISHER_ROLE)) {
                                roleList = roleList + ", " + Constants.PUBLISHER_ROLE;
                                mapping.put(Constants.ROLES, roleList);
                            }
                        }
                    }
                }

                ObjectMapper mapper = new ObjectMapper();
                String formattedTenantConf = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tenantConf);
                APIUtil.updateTenantConf(formattedTenantConf, tenant.getDomain());
                log.info("Updated scope roles of tenant-conf.json for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')'
                        + "\n" + formattedTenantConf);
            } catch (APIManagementException e) {
                log.error("Error while updating scope role mappings in tenant-conf.json. ", e);
            } catch (JsonProcessingException e) {
                log.error("Error while formatting tenant-conf.json of tenant " + tenant.getId());
            } finally {
                registryService.endTenantFlow();
            }
        }

        log.info("Updating Scope-Role Mappings is complete for all tenants");
    }

    @Override
    public void scopeMigration() throws APIMigrationException {
    }

    @Override
    public void spMigration() throws APIMigrationException {
    }

    /**
     * This method is used to update the scopes of the user roles which will be retrieved based on the
     * permissions assigned.
     */
    public void populateRoleMappingWithUserRoles() throws APIMigrationException {
        log.info("Updating User Roles based on Permissions started.");

        for (Tenant tenant : getTenantsArray()) {
            try {
                registryService.startTenantFlow(tenant);
                if (tenant.getId() != MultitenantConstants.SUPER_TENANT_ID) {
                    APIUtil.loadAndSyncTenantConf(tenant.getId());
                }

                log.info("Updating user roles for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')');

                // Retrieve user roles which has create permission
                List<UserRoleFromPermissionDTO> userRolesListWithCreatePermission = SharedDAO.getInstance()
                        .getRoleNamesMatchingPermission(Constants.API_CREATE, tenant.getId());

                // Retrieve user roles which has publish permission
                List<UserRoleFromPermissionDTO> userRolesListWithPublishPermission = SharedDAO.getInstance()
                        .getRoleNamesMatchingPermission(Constants.API_PUBLISH, tenant.getId());

                // Retrieve user roles which has subscribe permission
                List<UserRoleFromPermissionDTO> userRolesListWithSubscribePermission = SharedDAO.getInstance()
                        .getRoleNamesMatchingPermission(Constants.API_SUBSCRIBE, tenant.getId());

                // Retrieve user roles which has manage API permission
                List<UserRoleFromPermissionDTO> userRolesListWithManageAPIPermission = SharedDAO.getInstance()
                        .getRoleNamesMatchingPermission(Constants.API_MANAGE, tenant.getId());
                userRolesListWithCreatePermission.addAll(userRolesListWithManageAPIPermission);
                userRolesListWithPublishPermission.addAll(userRolesListWithManageAPIPermission);
                userRolesListWithSubscribePermission.addAll(userRolesListWithManageAPIPermission);

                // Retrieve user roles which has admin permissions
                List<UserRoleFromPermissionDTO> userRolesListWithAdminPermission = SharedDAO.getInstance()
                        .getRoleNamesMatchingPermissions(makePermissionsStringByEscapingSlash(
                                Constants.APIM_ADMIN, "/permission"), tenant.getId());

                // Retrieve the tenant-conf.json of the corresponding tenant
                JSONObject tenantConf = APIUtil.getTenantConfig(tenant.getDomain());

                // Extract the RoleMappings object (This will be null if this does not exist at the moment)
                JSONObject roleMappings = (JSONObject) tenantConf.get(APIConstants.REST_API_ROLE_MAPPINGS_CONFIG);
                if (roleMappings == null) {
                    // Create RoleMappings field in tenant-conf.json and retrieve the object
                    tenantConf.put(APIConstants.REST_API_ROLE_MAPPINGS_CONFIG, new JSONObject());
                    roleMappings = (JSONObject) tenantConf.get(APIConstants.REST_API_ROLE_MAPPINGS_CONFIG);
                }

                createOrUpdateRoleMappingsField(roleMappings, userRolesListWithCreatePermission,
                        userRolesListWithPublishPermission, userRolesListWithSubscribePermission,
                        userRolesListWithAdminPermission);

                ObjectMapper mapper = new ObjectMapper();
                String formattedTenantConf = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tenantConf);

                APIUtil.updateTenantConf(formattedTenantConf, tenant.getDomain());
                log.info("Updated tenant-conf.json for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')'
                        + "\n" + formattedTenantConf);

                log.info("End updating user roles for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')');
            } catch (APIManagementException e) {
                log.error("Error while retrieving role names based on existing permissions. ", e);
            } catch (JsonProcessingException e) {
                log.error("Error while formatting tenant-conf.json of tenant " + tenant.getId());
            } finally {
                registryService.endTenantFlow();
            }
        }
        log.info("Updating User Roles done for all the tenants.");
    }

    /**
     * This method is used to retrieve user roles as a comma separated string
     */
    private String getUserRoleArrayAsString(List<UserRoleFromPermissionDTO> userRoleFromPermissionDTOs) {
        List<String> updatedUserRoles = new ArrayList<>();
        for (UserRoleFromPermissionDTO userRoleFromPermissionDTO : userRoleFromPermissionDTOs) {
            String userRoleName = userRoleFromPermissionDTO.getUserRoleName();
            String domainName = userRoleFromPermissionDTO.getUserRoleDomainName();
            updatedUserRoles.add(addDomainToName(userRoleName, domainName));
        }
        return StringUtils.join(updatedUserRoles, ",");
    }

    /**
     * This method is used to retrieve merged existing role mappings and new user roles
     */
    private String getMergedUserRolesAndRoleMappings(List<UserRoleFromPermissionDTO> userRoles, String roleMappings) {
        // Splitting
        ArrayList<String> roleMappingsArray = new ArrayList<String>(Arrays.asList(StringUtils.
                split(roleMappings, ",")));
        // Trimming
        for (int i = 0; i < roleMappingsArray.size(); i++)
            roleMappingsArray.set(i, roleMappingsArray.get(i).trim());

        for (UserRoleFromPermissionDTO userRole : userRoles) {
            String domainNameAddedUserRoleName = addDomainToName(userRole.getUserRoleName(), userRole.getUserRoleDomainName());
            if (!roleMappingsArray.contains(domainNameAddedUserRoleName)) {
                roleMappingsArray.add(domainNameAddedUserRoleName);
            }
        }
        return StringUtils.join(roleMappingsArray, ",");
    }

    /**
     * This method is used to add the fields (Internal/creator, Internal/publisher and Internal/subscriber) and
     * assign the created user roles list as values to the object
     */
    private void createOrUpdateRoleMappingsField(JSONObject roleMappings,
                                                 List<UserRoleFromPermissionDTO> userRolesListWithCreatePermission,
                                                 List<UserRoleFromPermissionDTO> userRolesListWithPublishPermission,
                                                 List<UserRoleFromPermissionDTO> userRolesListWithSubscribePermission,
                                                 List<UserRoleFromPermissionDTO> userRolesListWithAdminPermission) {
        if (userRolesListWithCreatePermission.size() > 0) {
            if (roleMappings.get(Constants.CREATOR_ROLE) == null) {
                roleMappings.put(Constants.CREATOR_ROLE,
                        getUserRoleArrayAsString(userRolesListWithCreatePermission));
            } else {
                roleMappings.put(
                        Constants.CREATOR_ROLE,
                        getMergedUserRolesAndRoleMappings(userRolesListWithCreatePermission,
                                String.valueOf(roleMappings.get(Constants.CREATOR_ROLE))));
            }
        }

        if (userRolesListWithPublishPermission.size() > 0) {
            if (roleMappings.get(Constants.PUBLISHER_ROLE) == null) {
                roleMappings
                        .put(Constants.PUBLISHER_ROLE, getUserRoleArrayAsString(userRolesListWithPublishPermission));
            } else {
                roleMappings.put(Constants.PUBLISHER_ROLE,
                        getMergedUserRolesAndRoleMappings(userRolesListWithPublishPermission,
                                String.valueOf(roleMappings.get(Constants.PUBLISHER_ROLE))));
            }
        }

        if (userRolesListWithSubscribePermission.size() > 0) {
            if (roleMappings.get(Constants.SUBSCRIBER_ROLE) == null) {
                roleMappings
                        .put(Constants.SUBSCRIBER_ROLE, getUserRoleArrayAsString(userRolesListWithSubscribePermission));
            } else {
                roleMappings.put(Constants.SUBSCRIBER_ROLE,
                        getMergedUserRolesAndRoleMappings(userRolesListWithSubscribePermission,
                                String.valueOf(roleMappings.get(Constants.SUBSCRIBER_ROLE))));
            }
        }

        if (userRolesListWithAdminPermission.size() > 0) {
            if (roleMappings.get(Constants.ADMIN_ROLE) == null) {
                roleMappings.put(Constants.ADMIN_ROLE, getUserRoleArrayAsString(userRolesListWithAdminPermission));
            } else {
                roleMappings.put(Constants.ADMIN_ROLE,
                        getMergedUserRolesAndRoleMappings(userRolesListWithAdminPermission,
                                String.valueOf(roleMappings.get(Constants.ADMIN_ROLE))));
            }
        }
    }

    /**
     * This method is used to retrieve a string where the domain name is added in front of the user role name
     */
    private String addDomainToName(String userRoleName, String domainName) {
        if (StringUtils.equals(domainName.toLowerCase(), Constants.USER_DOMAIN_INTERNAL.toLowerCase())) {
            // This check should be done for domain names with "Internal". Otherwise addDomainToName function will
            // convert this to uppercase (INTERNAL).
            return Constants.USER_DOMAIN_INTERNAL + "/" + userRoleName;
        } else {
            return UserCoreUtil.addDomainToName(userRoleName, domainName);
        }
    }

    /**
     * This method is used to retrieve a string with multiple permissions by escaping slashes
     * Example: If you provide "/permission/mypermission/" as startPermission and "/permission" as endPermission
     * this will produces a string as "'/permission/mypermission/', '/permission/mypermission', '/permission/,
     * '/permission'"
     */
    private String makePermissionsStringByEscapingSlash(String startPermission, String endPermission) {
        StringBuilder permissions = new StringBuilder();
        permissions.append("'").append(startPermission).append("', ");
        for (int i = startPermission.length() - 1; i >= 0; i--) {
            if (!StringUtils.equals(startPermission.substring(0, i + 1), endPermission)) {
                if (startPermission.charAt(i) == '/') {
                    permissions.append("'").append(startPermission, 0, i + 1).append("', ");
                    permissions.append("'").append(startPermission, 0, i).append("', ");
                }
            } else {
                break;
            }
        }
        return StringUtils.chop(permissions.toString().trim());
    }
}
