package com.banking.tests.api;

import com.banking.tests.base.BaseApiTest;
import io.restassured.http.ContentType;
import org.testng.annotations.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * AUTH regression tests — covers both layers of authentication.
 *
 * Layer 1: POST /v1/auth/login    (username/password -> authorization code)
 * Layer 2: POST /v1/oauth/token   (code + grant_type -> access token)
 * Layer 3: using tokens on protected endpoints
 */
public class AuthTests extends BaseApiTest {

    // =========================================================
    // POSITIVE SCENARIOS
    // =========================================================

    @Test(description = "TC_AUTH_001: Valid login returns an authorization code")
    public void loginWithValidCredentials() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", VALID_USER, "password", VALID_PASSWORD))
        .when()
            .post("/v1/auth/login")
        .then()
            .statusCode(200)                                    // request succeeded
            .body("status", equalTo("AUTHENTICATED"))           // logged in
            .body("authorizationCode", startsWith("AUTHCODE-")) // got a code
            .body("expiresInSeconds", equalTo(300));            // code lives 5 min
    }

    @Test(description = "TC_AUTH_002: Full 2-layer flow — code exchanges for access token")
    public void fullTwoLayerFlow() {
        // Layer 1: login -> grab the code from the response
        String code = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", VALID_USER, "password", VALID_PASSWORD))
            .when().post("/v1/auth/login")
            .then().statusCode(200)
            .extract().path("authorizationCode");

        // Layer 2: exchange the code with grant_type
        String token = given()
                .contentType(ContentType.JSON)
                .body(Map.of("grant_type", "authorization_code", "code", code))
            .when().post("/v1/oauth/token")
            .then()
                .statusCode(200)
                .body("token_type", equalTo("Bearer"))
                .body("expires_in", equalTo(1800))
            .extract().path("access_token");

        // Layer 3: the new token actually works on a protected endpoint
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/v1/accounts/" + VALID_ACCOUNT_A + "/balance")
        .then()
            .statusCode(200)
            .body("accountId", equalTo(VALID_ACCOUNT_A));
    }

    // =========================================================
    // NEGATIVE SCENARIOS
    // =========================================================

    @Test(description = "TC_AUTH_003: Wrong password -> 401 INVALID_CREDENTIALS")
    public void loginWithWrongPassword() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", VALID_USER, "password", "WrongPass!"))
        .when()
            .post("/v1/auth/login")
        .then()
            .statusCode(401)
            .body("errorCode", equalTo("INVALID_CREDENTIALS"));
    }

    @Test(description = "TC_AUTH_004: Missing password -> 400 MISSING_CREDENTIALS")
    public void loginWithMissingPassword() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", VALID_USER))       // no password field at all
        .when()
            .post("/v1/auth/login")
        .then()
            .statusCode(400)
            .body("errorCode", equalTo("MISSING_CREDENTIALS"));
    }

    @Test(description = "TC_AUTH_005: Wrong grant_type -> 400 UNSUPPORTED_GRANT_TYPE")
    public void tokenWithWrongGrantType() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("grant_type", "client_credentials", "code", "AUTHCODE-ANYTHING"))
        .when()
            .post("/v1/oauth/token")
        .then()
            .statusCode(400)
            .body("errorCode", equalTo("UNSUPPORTED_GRANT_TYPE"));
    }

    @Test(description = "TC_AUTH_006: Fake authorization code -> 401 INVALID_AUTH_CODE")
    public void tokenWithInvalidCode() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("grant_type", "authorization_code", "code", "AUTHCODE-FAKE12345"))
        .when()
            .post("/v1/oauth/token")
        .then()
            .statusCode(401)
            .body("errorCode", equalTo("INVALID_AUTH_CODE"));
    }

    @Test(description = "TC_AUTH_007: Auth code is single-use — second exchange fails")
    public void authCodeIsSingleUse() {
        // Get a real code
        String code = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", VALID_USER, "password", VALID_PASSWORD))
            .when().post("/v1/auth/login")
            .then().extract().path("authorizationCode");

        Map<String, String> body = Map.of("grant_type", "authorization_code", "code", code);

        // First exchange works
        given().contentType(ContentType.JSON).body(body)
            .when().post("/v1/oauth/token")
            .then().statusCode(200);

        // Second exchange with the SAME code must fail (like real OAuth)
        given().contentType(ContentType.JSON).body(body)
            .when().post("/v1/oauth/token")
            .then().statusCode(401)
            .body("errorCode", equalTo("INVALID_AUTH_CODE"));
    }

    @Test(description = "TC_AUTH_008: No Authorization header -> 401 MISSING_TOKEN")
    public void protectedEndpointWithoutToken() {
        given()   // note: NO Authorization header at all
        .when()
            .get("/v1/accounts/" + VALID_ACCOUNT_A + "/balance")
        .then()
            .statusCode(401)
            .body("errorCode", equalTo("MISSING_TOKEN"));
    }

    @Test(description = "TC_AUTH_009: Expired token -> 401 TOKEN_EXPIRED")
    public void protectedEndpointWithExpiredToken() {
        given()
            .header("Authorization", "Bearer " + EXPIRED_TOKEN)
        .when()
            .get("/v1/accounts/" + VALID_ACCOUNT_A + "/balance")
        .then()
            .statusCode(401)
            .body("errorCode", equalTo("TOKEN_EXPIRED"));
    }

    @Test(description = "TC_AUTH_010: Garbage token -> 401 INVALID_TOKEN")
    public void protectedEndpointWithGarbageToken() {
        given()
            .header("Authorization", "Bearer TOTALLY-FAKE-TOKEN")
        .when()
            .get("/v1/accounts/" + VALID_ACCOUNT_A + "/balance")
        .then()
            .statusCode(401)
            .body("errorCode", equalTo("INVALID_TOKEN"));
    }
}
