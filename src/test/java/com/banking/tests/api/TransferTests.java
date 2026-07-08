package com.banking.tests.api;

import com.banking.tests.base.BaseApiTest;
import io.restassured.http.ContentType;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * FUND TRANSFER regression tests — POST /v1/transfers
 * Covers the happy path, every business-rule rejection, and idempotency.
 */
public class TransferTests extends BaseApiTest {

    /** Helper: builds a valid transfer body we can then break on purpose */
    private Map<String, Object> validTransferBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("fromAccount",    VALID_ACCOUNT_A);
        body.put("toAccount",      VALID_ACCOUNT_B);
        body.put("amount",         100.00);
        body.put("currency",       "USD");
        body.put("remarks",        "Regression test transfer");
        body.put("idempotencyKey", newIdemKey());
        return body;
    }

    /** Helper: sends the transfer request with the dynamic access token */
    private io.restassured.response.Response postTransfer(Map<String, Object> body) {
        return given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(body)
            .when()
                .post("/v1/transfers");
    }

    // =========================================================
    // POSITIVE SCENARIOS
    // =========================================================

    @Test(description = "TC_FT_001: Valid transfer succeeds and debits/credits correctly")
    public void validTransferSucceeds() {
        postTransfer(validTransferBody())
            .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("transactionId", startsWith("TXN-"))
                .body("amount", equalTo(100.0f));

        // Verify the money actually moved: A had 5000, now 4900
        given().header("Authorization", "Bearer " + accessToken)
            .when().get("/v1/accounts/" + VALID_ACCOUNT_A + "/balance")
            .then().body("availableBalance", equalTo(4900.0f));

        // B had 2000, now 2100
        given().header("Authorization", "Bearer " + accessToken)
            .when().get("/v1/accounts/" + VALID_ACCOUNT_B + "/balance")
            .then().body("availableBalance", equalTo(2100.0f));
    }

    @Test(description = "TC_FT_002: Same idempotency key twice -> same transaction, no double debit")
    public void idempotencyPreventsDoubleDebit() {
        Map<String, Object> body = validTransferBody();   // fixed key for both calls

        // First call — real transfer happens
        String txnId1 = postTransfer(body).then().statusCode(200)
                .extract().path("transactionId");

        // Second call with the SAME key — must return the SAME transaction
        String txnId2 = postTransfer(body).then().statusCode(200)
                .extract().path("transactionId");

        org.testng.Assert.assertEquals(txnId2, txnId1,
                "Same idempotency key must return the same transaction");

        // Balance debited only ONCE: 5000 - 100 = 4900 (not 4800)
        given().header("Authorization", "Bearer " + accessToken)
            .when().get("/v1/accounts/" + VALID_ACCOUNT_A + "/balance")
            .then().body("availableBalance", equalTo(4900.0f));
    }

    @Test(description = "TC_FT_003: Boundary — transfer of exactly the full balance succeeds")
    public void transferExactFullBalance() {
        Map<String, Object> body = validTransferBody();
        body.put("fromAccount", "ACC-300001");  // seeded with exactly $1000
        body.put("amount", 1000.00);            // whole balance -> should PASS
        postTransfer(body).then().statusCode(200).body("status", equalTo("SUCCESS"));
    }

    // =========================================================
    // NEGATIVE SCENARIOS
    // =========================================================

    @Test(description = "TC_FT_004: Insufficient funds -> 422 INSUFFICIENT_FUNDS")
    public void insufficientFunds() {
        Map<String, Object> body = validTransferBody();
        body.put("amount", 999999.00);          // way more than the $5000 balance
        postTransfer(body).then()
            .statusCode(422)
            .body("errorCode", equalTo("INSUFFICIENT_FUNDS"));
    }

    @Test(description = "TC_FT_005: Boundary — one cent over the balance fails")
    public void oneCentOverBalanceFails() {
        Map<String, Object> body = validTransferBody();
        body.put("fromAccount", "ACC-300001");  // exactly $1000
        body.put("amount", 1000.01);            // one cent too much -> FAIL
        postTransfer(body).then()
            .statusCode(422)
            .body("errorCode", equalTo("INSUFFICIENT_FUNDS"));
    }

    @Test(description = "TC_FT_006: Same source and destination -> 400 SAME_ACCOUNT_TRANSFER")
    public void sameAccountTransfer() {
        Map<String, Object> body = validTransferBody();
        body.put("toAccount", VALID_ACCOUNT_A);  // same as fromAccount
        postTransfer(body).then()
            .statusCode(400)
            .body("errorCode", equalTo("SAME_ACCOUNT_TRANSFER"));
    }

    @Test(description = "TC_FT_007: Unknown source account -> 404 SOURCE_ACCOUNT_NOT_FOUND")
    public void sourceAccountNotFound() {
        Map<String, Object> body = validTransferBody();
        body.put("fromAccount", NON_EXISTENT_ACCT);
        postTransfer(body).then()
            .statusCode(404)
            .body("errorCode", equalTo("SOURCE_ACCOUNT_NOT_FOUND"));
    }

    @Test(description = "TC_FT_008: Unknown destination account -> 404 DESTINATION_ACCOUNT_NOT_FOUND")
    public void destinationAccountNotFound() {
        Map<String, Object> body = validTransferBody();
        body.put("toAccount", NON_EXISTENT_ACCT);
        postTransfer(body).then()
            .statusCode(404)
            .body("errorCode", equalTo("DESTINATION_ACCOUNT_NOT_FOUND"));
    }

    @Test(description = "TC_FT_009: Frozen source account -> 403 ACCOUNT_FROZEN")
    public void frozenSourceAccount() {
        Map<String, Object> body = validTransferBody();
        body.put("fromAccount", FROZEN_ACCOUNT);
        postTransfer(body).then()
            .statusCode(403)
            .body("errorCode", equalTo("ACCOUNT_FROZEN"));
    }

    @Test(description = "TC_FT_010: Currency mismatch -> 422 CURRENCY_MISMATCH")
    public void currencyMismatch() {
        Map<String, Object> body = validTransferBody();
        body.put("currency", "GBP");             // source account is USD
        postTransfer(body).then()
            .statusCode(422)
            .body("errorCode", equalTo("CURRENCY_MISMATCH"));
    }

    @Test(description = "TC_FT_011: Negative amount -> 400 VALIDATION_ERROR")
    public void negativeAmount() {
        Map<String, Object> body = validTransferBody();
        body.put("amount", -50.00);              // DTO rule: amount must be > 0
        postTransfer(body).then()
            .statusCode(400)
            .body("errorCode", equalTo("VALIDATION_ERROR"));
    }

    @Test(description = "TC_FT_012: Zero amount -> 400 VALIDATION_ERROR")
    public void zeroAmount() {
        Map<String, Object> body = validTransferBody();
        body.put("amount", 0);
        postTransfer(body).then()
            .statusCode(400)
            .body("errorCode", equalTo("VALIDATION_ERROR"));
    }

    @Test(description = "TC_FT_013: Missing required field (toAccount) -> 400 VALIDATION_ERROR")
    public void missingToAccount() {
        Map<String, Object> body = validTransferBody();
        body.remove("toAccount");
        postTransfer(body).then()
            .statusCode(400)
            .body("errorCode", equalTo("VALIDATION_ERROR"));
    }

    @Test(description = "TC_FT_014: Amount with 3 decimal places -> 400 VALIDATION_ERROR")
    public void threeDecimalPlaces() {
        Map<String, Object> body = validTransferBody();
        body.put("amount", 100.999);             // DTO rule: max 2 decimals
        postTransfer(body).then()
            .statusCode(400)
            .body("errorCode", equalTo("VALIDATION_ERROR"));
    }
}
