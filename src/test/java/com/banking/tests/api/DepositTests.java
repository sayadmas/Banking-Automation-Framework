package com.banking.tests.api;

import com.banking.tests.base.BaseApiTest;
import io.restassured.http.ContentType;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * DEPOSIT regression tests — POST /v1/deposits (NEW feature)
 */
public class DepositTests extends BaseApiTest {

    /** Helper: a valid deposit body we can break on purpose */
    private Map<String, Object> validDepositBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("accountId",      VALID_ACCOUNT_A);
        body.put("amount",         500.00);
        body.put("currency",       "USD");
        body.put("depositType",    "MOBILE_CHECK");
        body.put("remarks",        "Regression test deposit");
        body.put("idempotencyKey", newIdemKey());
        return body;
    }

    private io.restassured.response.Response postDeposit(Map<String, Object> body) {
        return given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(body)
            .when()
                .post("/v1/deposits");
    }

    // =========================================================
    // POSITIVE SCENARIOS
    // =========================================================

    @Test(description = "TC_DEP_001: Valid deposit returns 201 and credits the account")
    public void validDepositSucceeds() {
        postDeposit(validDepositBody())
            .then()
                .statusCode(201)                              // new record created
                .body("status", equalTo("SUCCESS"))
                .body("transactionId", startsWith("DEP-"))
                .body("balanceAfter", equalTo(5500.0f));      // 5000 + 500

        // Double-check via the balance endpoint
        given().header("Authorization", "Bearer " + accessToken)
            .when().get("/v1/accounts/" + VALID_ACCOUNT_A + "/balance")
            .then().body("availableBalance", equalTo(5500.0f));
    }

    @Test(description = "TC_DEP_002: Boundary — deposit exactly at the $50,000 limit succeeds")
    public void depositExactlyAtLimit() {
        Map<String, Object> body = validDepositBody();
        body.put("amount", 50000.00);            // at the limit -> allowed
        postDeposit(body).then().statusCode(201).body("status", equalTo("SUCCESS"));
    }

    @Test(description = "TC_DEP_003: Same idempotency key twice -> no double credit")
    public void idempotencyPreventsDoubleCredit() {
        Map<String, Object> body = validDepositBody();   // fixed key

        postDeposit(body).then().statusCode(201);
        postDeposit(body).then().statusCode(201);        // replay

        // Credited only ONCE: 5000 + 500 = 5500 (not 6000)
        given().header("Authorization", "Bearer " + accessToken)
            .when().get("/v1/accounts/" + VALID_ACCOUNT_A + "/balance")
            .then().body("availableBalance", equalTo(5500.0f));
    }

    // =========================================================
    // NEGATIVE SCENARIOS
    // =========================================================

    @Test(description = "TC_DEP_004: One cent over the AML limit -> 422 DEPOSIT_LIMIT_EXCEEDED")
    public void depositOverLimit() {
        Map<String, Object> body = validDepositBody();
        body.put("amount", 50000.01);            // one cent over -> blocked
        postDeposit(body).then()
            .statusCode(422)
            .body("errorCode", equalTo("DEPOSIT_LIMIT_EXCEEDED"));
    }

    @Test(description = "TC_DEP_005: Deposit to unknown account -> 404 ACCOUNT_NOT_FOUND")
    public void depositToUnknownAccount() {
        Map<String, Object> body = validDepositBody();
        body.put("accountId", NON_EXISTENT_ACCT);
        postDeposit(body).then()
            .statusCode(404)
            .body("errorCode", equalTo("ACCOUNT_NOT_FOUND"));
    }

    @Test(description = "TC_DEP_006: Deposit to frozen account -> 403 ACCOUNT_FROZEN")
    public void depositToFrozenAccount() {
        Map<String, Object> body = validDepositBody();
        body.put("accountId", FROZEN_ACCOUNT);
        postDeposit(body).then()
            .statusCode(403)
            .body("errorCode", equalTo("ACCOUNT_FROZEN"));
    }

    @Test(description = "TC_DEP_007: Wrong currency -> 422 CURRENCY_MISMATCH")
    public void depositWrongCurrency() {
        Map<String, Object> body = validDepositBody();
        body.put("currency", "GBP");             // account is USD
        postDeposit(body).then()
            .statusCode(422)
            .body("errorCode", equalTo("CURRENCY_MISMATCH"));
    }

    @Test(description = "TC_DEP_008: Invalid depositType -> 400 VALIDATION_ERROR")
    public void invalidDepositType() {
        Map<String, Object> body = validDepositBody();
        body.put("depositType", "BITCOIN");      // not CASH/CHECK/MOBILE_CHECK
        postDeposit(body).then()
            .statusCode(400)
            .body("errorCode", equalTo("VALIDATION_ERROR"));
    }

    @Test(description = "TC_DEP_009: Negative deposit amount -> 400 VALIDATION_ERROR")
    public void negativeDepositAmount() {
        Map<String, Object> body = validDepositBody();
        body.put("amount", -100.00);
        postDeposit(body).then()
            .statusCode(400)
            .body("errorCode", equalTo("VALIDATION_ERROR"));
    }
}
