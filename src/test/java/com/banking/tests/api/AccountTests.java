package com.banking.tests.api;

import com.banking.tests.base.BaseApiTest;
import org.testng.annotations.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * ACCOUNT regression tests — balance enquiry + transaction history
 *   GET /v1/accounts/{id}/balance
 *   GET /v1/accounts/{id}/transactions
 */
public class AccountTests extends BaseApiTest {

    // =========================================================
    // BALANCE — positive + negative
    // =========================================================

    @Test(description = "TC_ACC_001: Balance for a valid account returns full details")
    public void balanceForValidAccount() {
        given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .get("/v1/accounts/" + VALID_ACCOUNT_A + "/balance")
        .then()
            .statusCode(200)
            .body("accountId",        equalTo(VALID_ACCOUNT_A))
            .body("currentBalance",   equalTo(5000.0f))
            .body("availableBalance", equalTo(5000.0f))
            .body("currency",         equalTo("USD"))
            .body("status",           equalTo("ACTIVE"));
    }

    @Test(description = "TC_ACC_002: Balance for unknown account -> 404 ACCOUNT_NOT_FOUND")
    public void balanceForUnknownAccount() {
        given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .get("/v1/accounts/" + NON_EXISTENT_ACCT + "/balance")
        .then()
            .statusCode(404)
            .body("errorCode", equalTo("ACCOUNT_NOT_FOUND"));
    }

    @Test(description = "TC_ACC_003: Balance for frozen account -> 403 ACCOUNT_FROZEN")
    public void balanceForFrozenAccount() {
        given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .get("/v1/accounts/" + FROZEN_ACCOUNT + "/balance")
        .then()
            .statusCode(403)
            .body("errorCode", equalTo("ACCOUNT_FROZEN"));
    }

    // =========================================================
    // TRANSACTION HISTORY — positive + negative
    // =========================================================

    @Test(description = "TC_ACC_004: History returns the 8 seeded transactions")
    public void historyReturnsSeededTransactions() {
        given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .get("/v1/accounts/" + VALID_ACCOUNT_A + "/transactions")
        .then()
            .statusCode(200)
            .body("totalCount", equalTo(8))
            .body("transactions.size()", equalTo(8));
    }

    @Test(description = "TC_ACC_005: type=DEBIT filter returns only debits")
    public void historyDebitFilter() {
        given()
            .header("Authorization", "Bearer " + accessToken)
            .queryParam("type", "DEBIT")
        .when()
            .get("/v1/accounts/" + VALID_ACCOUNT_A + "/transactions")
        .then()
            .statusCode(200)
            // 'everyItem' checks that EVERY transaction in the list is a DEBIT
            .body("transactions.type", everyItem(equalTo("DEBIT")));
    }

    @Test(description = "TC_ACC_006: Pagination — pageSize=3 returns exactly 3 items")
    public void historyPagination() {
        given()
            .header("Authorization", "Bearer " + accessToken)
            .queryParam("page", 1)
            .queryParam("pageSize", 3)
        .when()
            .get("/v1/accounts/" + VALID_ACCOUNT_A + "/transactions")
        .then()
            .statusCode(200)
            .body("transactions.size()", equalTo(3))
            .body("totalCount", equalTo(8));   // total stays 8 even when paged
    }

    @Test(description = "TC_ACC_007: Bad date format -> 400 INVALID_DATE_FORMAT")
    public void historyBadDateFormat() {
        given()
            .header("Authorization", "Bearer " + accessToken)
            .queryParam("fromDate", "07-01-2026")   // wrong format (needs YYYY-MM-DD)
        .when()
            .get("/v1/accounts/" + VALID_ACCOUNT_A + "/transactions")
        .then()
            .statusCode(400)
            .body("errorCode", equalTo("INVALID_DATE_FORMAT"));
    }

    @Test(description = "TC_ACC_008: fromDate after toDate -> 400 INVALID_DATE_RANGE")
    public void historyInvalidDateRange() {
        given()
            .header("Authorization", "Bearer " + accessToken)
            .queryParam("fromDate", "2026-07-01")
            .queryParam("toDate",   "2026-06-01")   // ends before it starts
        .when()
            .get("/v1/accounts/" + VALID_ACCOUNT_A + "/transactions")
        .then()
            .statusCode(400)
            .body("errorCode", equalTo("INVALID_DATE_RANGE"));
    }
}
