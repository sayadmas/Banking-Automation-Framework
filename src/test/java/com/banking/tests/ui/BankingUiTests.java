package com.banking.tests.ui;

import com.banking.tests.ui.pages.DashboardPage;
import com.banking.tests.ui.pages.LoginPage;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.*;

import java.util.logging.Level;

import static io.restassured.RestAssured.given;

/**
 * SELENIUM UI regression tests for the mock bank web page
 * at http://localhost:9090/ui/index.html
 *
 * Uses the Page Object Model:
 *   LoginPage      -> login form + error message
 *   DashboardPage  -> balance check + transfer form
 *
 * DIAGNOSTIC UPGRADE: Chrome's browser console is captured, and if a test
 * fails, every console message (JS errors, failed network calls) is printed
 * to the Maven output — so a failure tells us WHY, not just THAT.
 */
public class BankingUiTests {

    private static final String BASE_URL = "http://localhost:9090";

    private WebDriver driver;
    private LoginPage loginPage;
    private DashboardPage dashboardPage;

    @BeforeClass(alwaysRun = true)
    public void setUpDriver() {
        // WebDriverManager downloads the matching ChromeDriver — no manual setup
        WebDriverManager.chromedriver().setup();
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        // Reset bank data so every UI test starts from the same balances
        io.restassured.RestAssured.baseURI = BASE_URL;
        given().post("/v1/admin/reset").then().statusCode(200);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");   // remove this line to WATCH the browser
        options.addArguments("--window-size=1280,1800");

        // Capture the browser console so failures are diagnosable
        LoggingPreferences logPrefs = new LoggingPreferences();
        logPrefs.enable(LogType.BROWSER, Level.ALL);
        options.setCapability("goog:loggingPrefs", logPrefs);

        driver = new ChromeDriver(options);

        loginPage     = new LoginPage(driver);
        dashboardPage = new DashboardPage(driver);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        try {
            // On failure, dump the browser console — this shows the REAL error
            // (JS exception, 403 on a fetch, etc.) in the Maven output.
            if (!result.isSuccess() && driver != null) {
                System.out.println("==== BROWSER CONSOLE for FAILED test: "
                        + result.getName() + " ====");
                for (LogEntry entry : driver.manage().logs().get(LogType.BROWSER)) {
                    System.out.println("  [" + entry.getLevel() + "] " + entry.getMessage());
                }
                System.out.println("==== current page state ====");
                System.out.println("  URL: " + driver.getCurrentUrl());
                System.out.println("============================================");
            }
        } catch (Exception ignored) {
            // console capture is best-effort; never let it mask the real failure
        } finally {
            if (driver != null) driver.quit();   // always close the browser
        }
    }

    // =========================================================
    // TESTS
    // =========================================================

    @Test(description = "TC_UI_001: Valid login shows the dashboard with a welcome message")
    public void validLoginShowsDashboard() {
        loginPage.open(BASE_URL).login("subash", "Password@123");
        String welcome = dashboardPage.getWelcomeMessage();
        Assert.assertTrue(welcome.contains("subash"),
                "Welcome message should greet the logged-in user, got: " + welcome);
    }

    @Test(description = "TC_UI_002: Wrong password shows an error and stays on login")
    public void invalidLoginShowsError() {
        loginPage.open(BASE_URL).login("subash", "WrongPassword!");
        String error = loginPage.getErrorMessage();
        Assert.assertTrue(error.toLowerCase().contains("invalid"),
                "Error should mention invalid credentials, got: " + error);
    }

    @Test(description = "TC_UI_003: Balance check shows the seeded $5,000")
    public void balanceCheckShowsSeededAmount() {
        loginPage.open(BASE_URL).login("subash", "Password@123");
        dashboardPage.getWelcomeMessage();                 // wait for dashboard
        String balance = dashboardPage.checkBalance("ACC-100001");
        Assert.assertTrue(balance.contains("5000.00"),
                "Balance should show 5000.00, got: " + balance);
    }

    @Test(description = "TC_UI_004: Valid transfer via the UI shows a success message")
    public void validTransferShowsSuccess() {
        loginPage.open(BASE_URL).login("subash", "Password@123");
        dashboardPage.getWelcomeMessage();
        dashboardPage.makeTransfer("ACC-100001", "ACC-100002", "100.00", "UI test");
        String msg = dashboardPage.getTransferSuccessMessage();
        Assert.assertTrue(msg.contains("Transaction ID"),
                "Success message should include a transaction ID, got: " + msg);
    }

    @Test(description = "TC_UI_005: Transfer over the balance shows the API error on screen")
    public void overdraftTransferShowsError() {
        loginPage.open(BASE_URL).login("subash", "Password@123");
        dashboardPage.getWelcomeMessage();
        dashboardPage.makeTransfer("ACC-100001", "ACC-100002", "999999", "UI negative test");
        String msg = dashboardPage.getTransferErrorMessage();
        Assert.assertTrue(msg.toLowerCase().contains("balance")
                        || msg.toLowerCase().contains("insufficient"),
                "Error should mention the balance problem, got: " + msg);
    }
}