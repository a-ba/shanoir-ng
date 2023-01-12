package org.shanoir.ng.utils;

import org.keycloak.representations.AccessTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Keycloak Service account utility class
 *
 * @author Alae Es-saki
 */
@Component
public class KeycloakServiceAccountUtils {

    /**
     * Logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(KeycloakServiceAccountUtils.class);
    private final String GRANT_TYPE="client_credentials";

    @Value("${service-account.token.uri}")
    private String serverUrl;
    @Value("${service-account.client.id}")
    private String clientId;
    @Value("${service-account.client.credential-secret}")
    private String clientSecret;

    @Autowired
    private RestTemplate restTemplate;


    /**
     * Get an access token using service account
     *
     * @return AccessTokenResponse
     */
    public AccessTokenResponse getServiceAccountAccessToken(){

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("client_id", this.clientId);
        map.add("client_secret", this.clientSecret);
        map.add("grant_type", this.GRANT_TYPE);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(map, headers);

        try{
            ResponseEntity<AccessTokenResponse> response = this.restTemplate.exchange(this.serverUrl, HttpMethod.POST, entity, AccessTokenResponse.class);
            LOG.info("token retrieved !");

            return response.getBody();
        }catch(HttpStatusCodeException e){
            // in case of error with a response payload.
            LOG.error("error response, message : {}",e.getStatusCode(), e.getMessage());
            e.printStackTrace();
        }catch (RestClientException e){
            // in case of an error but no response payload;
            LOG.error("there is no response payload");
            e.printStackTrace();
        }

        return null;
    }
}
