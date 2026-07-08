package com.banking.tests.api;

import com.banking.tests.base.BaseApiTest;
import io.restassured.http.ContentType;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * BILL PAYMENT regression tests — GET /v1/billers + POST /v1/billpayments (NEW feature)
 */
public class BillPaymentTests extends BaseApiTest {

    private static final String VALID_BILLER = "BILLER-ELEC-001";

    /** Helper: a valid bill payment body we can break on purpose */
    private Map<String, Object> validBillBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("fromAccount",    VALID_ACCOUNT_A);
        body.put("billerId",       VALID_BILLER);
        body.put("consumerNumber", "ELEC-STMT-778899");
        body.put("amount",         85.75);
        body.put("currency",       "USD");
        body.put("remarks",        "July electricity bill");
        body.put("idempotencyKey", newIdemKey());
        return body;
    }

    private io.restassured.response.Response postBill(Map<String, Object> body) {
        return given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(body)
            .when()
                .post("/v1/billpayments");
    }

    // =========================================================
    // POSITIVE SCENARIOS
    // =========================================================

    @Test(description = "TC_BILL_001: Biller directory returns all 4 seeded billers")
    public void listBillers() {
        given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .get("/v1/billers")
        .then()
            .statusCode(200)
            .body("totalCount", equalTo(4))
            .body("billers.billerId", hasItem(VALID_BILLER));
    }

    @Test(description = "TC_BILL_002: Valid bill payment returns 201 with confirmation number")
    public void validBillPaymentSucceeds() {
        postBill(validBillBody())
            .then()
                .statusCode(201)
                .body("status", equalTo("SUCCESS"))
                .body("transactionId", startsWith("BILL-"))
                .body("billerName", equalTo("Duquesne Light Company"))
                .body("confirmationNumber", startsWith("CONF-"))
                .body("balanceAfter", equalTo(4914.25f));    // 5000 - 85.75

        // The debit is real — balance endpoint agrees
        given().header("Authorization", "Bearer " + accessToken)
            .when().get("/v1/accounts/" + VALID_ACCOUNT_A + "/balance")
            .then().body("availableBalance", equalTo(4914.25f));
    }

    @Test(description = "TC_BILL_003: Same idempotency key twice -> paid only once")
    public void idempotencyPreventsDoublePayment() {
        Map<String, Object> body = validBillBody();      // fixed key

        postBill(body).then().statusCode(201);
        postBill(body).then().statusCode(201);           // replay

        // Debited only ONCE
        given().header("Authorization", "Bearer " + accessToken)
            .when().get("/v1/accounts/" + VALID_ACCOUNT_A + "/balance")
            .then().body("availableBalance", equalTo(4914.25f));
    }

    // =========================================================
    // NEGATIVE SCENARIOS
    // =========================================================

    @Test(description = "TC_BILL_004: Unknown biller -> 404 BILLER_NOT_FOUND")
    public void unknownBiller() {
        Map<String, Object> body = validBillBody();
        body.put("billerId", "BILLER-FAKE-999");
        postBill(body).then()
            .statusCode(404)
            .body("errorCode", equalTo("BILLER_NOT_FOUND"));
    }

    @Test(description = "TC_BILL_005: Not enough money -> 422 INSUFFICIENT_FUNDS")
    public void insufficientFunds() {
        Map<String, Object> body = validBillBody();
        body.put("fromAccount", ZERO_BALANCE_ACCT);      // $0 account
        postBill(body).then()
            .statusCode(422)
            .body("errorCode", equalTo("INSUFFICIENT_FUNDS"));
    }

    @Test(description = "TC_BILL_006: Frozen account cannot pay -> 403 ACCOUNT_FROZEN")
    public void frozenAccountCannotPay() {
        Map<String, Object> body = validBillBody();
        body.put("fromAccount", FROZEN_ACCOUNT);
        postBill(body).then()
            .statusCode(403)
            .body("errorCode", equalTo("ACCOUNT_FROZEN"));
    }

    @Test(description = "TC_BILL_007: Missing consumerNumber -> 400 VALIDATION_ERROR")
    public void missingConsumerNumber() {
        Map<String, Object> body = validBillBody();
        body.remove("consumerNumber");
        postBill(body).then()
            .statusCode(400)
            .body("errorCode", equalTo("VALIDATION_ERROR"));
    }

    @Test(description = "TC_BILL_008: Wrong currency -> 422 CURRENCY_MISMATCH")
    public void wrongCurrency() {
        Map<String, Object> body = validBillBody();
        body.put("currency", "GBP");
        postBill(body).then()
            .statusCode(422)
            .body("errorCode", equalTo("CURRENCY_MISMATCH"));
    }
}
