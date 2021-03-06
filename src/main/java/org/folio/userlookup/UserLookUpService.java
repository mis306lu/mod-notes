package org.folio.userlookup;

import java.util.Map;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.RestVerticle;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.rest.tools.utils.TenantTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.json.JsonObject;

@Component
public class UserLookUpService {

  private static final Logger logger = LoggerFactory.getLogger(UserLookUpService.class);
  /**
   * Returns the user information for the userid specified in the original
   * request.
   *
   * @param okapiHeaders The headers for the current API call.
   * @return User information based on userid from header.
   */
  public Future<UserLookUp> getUserInfo(final Map<String, String> okapiHeaders) {
    CaseInsensitiveHeaders headers = new CaseInsensitiveHeaders();
    headers.addAll(okapiHeaders);

    final String tenantId = TenantTool.calculateTenantId(headers.get(RestVerticle.OKAPI_HEADER_TENANT));
    final String userId = headers.get(RestVerticle.OKAPI_USERID_HEADER);
    Promise<UserLookUp> promise = Promise.promise();
    if (userId == null) {
      logger.error("No userid header");
      promise.fail(new BadRequestException("Missing user id header, cannot look up user"));
      return promise.future();
    }

    String okapiURL = headers.get(XOkapiHeaders.URL);
    String url = "/users/" + userId;
    try {
      final HttpClientInterface httpClient = HttpClientFactory.getHttpClient(okapiURL, tenantId);
      httpClient.request(url, okapiHeaders)
        .thenApply(response -> {
          try {
            if (Response.isSuccess(response.getCode())) {
              return mapUserInfo(response);
            } else if (response.getCode() == 401 || response.getCode() == 403) {
              logger.error("Authorization failure");
              throw new NotAuthorizedException("Authorization failure");
            } else if (response.getCode() == 404) {
              logger.error("User not found");
              throw new NotFoundException("User not found");
            } else {
              logger.error("Cannot get user data: " + response.getError().toString(), response.getException());
              throw new IllegalStateException(response.getError().toString());
            }
          } finally {
            httpClient.closeClient();
          }
        })
        .thenAccept(promise::complete)
        .exceptionally(e -> {
          promise.fail(e.getCause());
          return null;
        });
    } catch (Exception e) {
      logger.error("Cannot get user data: " + e.getMessage(), e);
      promise.fail(e);
    }

    return promise.future();
  }

  private UserLookUp mapUserInfo(Response response) {
    UserLookUp.UserLookUpBuilder builder = UserLookUp.builder();
    JsonObject user = response.getBody();
    if (user.containsKey("username") && user.containsKey("personal")) {
      builder.userName(user.getString("username"));

      JsonObject personalInfo = user.getJsonObject("personal");
      if (personalInfo != null) {
        builder.firstName(personalInfo.getString("firstName"));
        builder.middleName(personalInfo.getString("middleName"));
        builder.lastName(personalInfo.getString("lastName"));
      }
    } else {
      throw new BadRequestException("Missing fields");
    }
    return builder.build();
  }
}
