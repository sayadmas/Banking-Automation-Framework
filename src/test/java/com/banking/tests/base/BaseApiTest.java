package com.banking.tests.base;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * BaseApiTest — every API test class extends this.
 *
 * What it does:
 *   1. Points RestAssured at the mock bank (http://localhost:9090)
 *   2. Before the suite: performs the full 2-layer login flow ONCE
 *      (login -> authorization code -> /v1/oauth/token -> access token)
 *      and saves the access token for all tests to reuse.
 *   3. Before every test method: resets the bank data so tests
 *      never depend on each other (clean state = reliable tests).
 */
public class BaseApiTest {

    // ---------- Test environment constants ----------
    protected static final String BASE_URL     = "http://localhost:9090";
    protected static final String STATIC_TOKEN = "BANKING-TEST-TOKEN-2024"; // legacy static token
    protected static final String EXPIRED_TOKEN = "EXPIRED-TOKEN-12345";

    // ---------- Seeded test data (matches DataStore.java) ----------
    protected static final String VALID_ACCOUNT_A    = "ACC-100001"; // $5,000 ACTIVE
    protected static final String VALID_ACCOUNT_B    = "ACC-100002"; // $2,000 ACTIVE
    protected static final String FROZEN_ACCOUNT     = "ACC-999001"; // FROZEN
    protected static final String ZERO_BALANCE_ACCT  = "ACC-999002"; // $0 ACTIVE
    protected static final String GBP_ACCOUNT        = "ACC-200001"; // GBP
    protected static final String NON_EXISTENT_ACCT  = "ACC-000000"; // not in store

    protected static final String VALID_USER     = "subash";
    protected static final String VALID_PASSWORD = "Password@123";

    /** The dynamic access token obtained via the 2-layer flow */
    protected static String accessToken;

    @BeforeSuite(alwaysRun = true)
    public void suiteSetup() {
        RestAssured.baseURI = BASE_URL;

        // ----- LAYER 1: login with username/password -> authorization code -----
        String authCode =
            given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", VALID_USER, "password", VALID_PASSWORD))
            .when()
                .post("/v1/auth/login")
            .then()
                .statusCode(200)
                .extract().path("authorizationCode");

        // ----- LAYER 2: exchange code + grant_type -> access token -----
        accessToken =
            given()
                .contentType(ContentType.JSON)
                .body(Map.of("grant_type", "authorization_code", "code", authCode))
            .when()
                .post("/v1/oauth/token")
            .then()
                .statusCode(200)
                .extract().path("access_token");
    }

    @BeforeMethod(alwaysRun = true)
    public void resetData() {
        // Reset accounts/transactions before EVERY test -> independent tests
        given().when().post("/v1/admin/reset").then().statusCode(200);
    }

    /** Helper: a fresh unique idempotency key for each request that needs one */
    protected String newIdemKey() {
        return "test-" + UUID.randomUUID();
    }
}
