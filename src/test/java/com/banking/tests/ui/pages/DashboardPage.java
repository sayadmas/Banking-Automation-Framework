package com.banking.tests.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Page Object Model (POM) for the DASHBOARD page
 * (visible after a successful login: balance check + fund transfer).
 *
 * RELIABLE CLICKS: buttons are clicked with a scroll-into-view + JavaScript
 * click helper instead of a raw coordinate-based click. Reason: elements
 * BELOW THE FOLD (like the Transfer button) can be silently missed by
 * native clicks when the Selenium version is older than the Chrome version
 * (headless scroll/click coordinate mismatch). A JS click is dispatched
 * directly to the element, so page position can never matter.
 */
public class DashboardPage {

    private final WebDriver driver;
    private final WebDriverWait wait;

    // ---------- Locators ----------
    private final By welcomeMessage  = By.id("welcome-message");
    private final By balanceAccount  = By.id("balance-account");
    private final By checkBalanceBtn = By.id("check-balance-btn");
    private final By balanceDisplay  = By.id("balance-display");
    private final By fromAccount     = By.id("from-account");
    private final By toAccount       = By.id("to-account");
    private final By amountField     = By.id("amount");
    private final By remarksField    = By.id("remarks");
    private final By transferBtn     = By.id("transfer-btn");
    private final By transferSuccess = By.id("transfer-success");
    private final By transferError   = By.id("transfer-error");

    public DashboardPage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    /** Wait for the dashboard to appear, then return the welcome text */
    public String getWelcomeMessage() {
        wait.until(ExpectedConditions.visibilityOfElementLocated(welcomeMessage));
        return driver.findElement(welcomeMessage).getText();
    }

    /** Pick an account from the dropdown and click Check Balance */
    public String checkBalance(String accountId) {
        new Select(driver.findElement(balanceAccount)).selectByValue(accountId);
        clickReliably(checkBalanceBtn);
        wait.until(d -> {
            String t = d.findElement(balanceDisplay).getText();
            return !t.isBlank() && !t.equals("Loading...");   // wait for a real result
        });
        return driver.findElement(balanceDisplay).getText();
    }

    /** Fill in the transfer form and submit it */
    public void makeTransfer(String from, String to, String amount, String remarks) {
        setField(fromAccount, from);
        setField(toAccount, to);
        setField(amountField, amount);
        setField(remarksField, remarks);
        clickReliably(transferBtn);
    }

    public String getTransferSuccessMessage() {
        wait.until(d -> !d.findElement(transferSuccess).getText().isBlank());
        return driver.findElement(transferSuccess).getText();
    }

    public String getTransferErrorMessage() {
        wait.until(d -> !d.findElement(transferError).getText().isBlank());
        return driver.findElement(transferError).getText();
    }

    // ---------- Helpers ----------

    /**
     * Scroll the element to the middle of the viewport, then dispatch the
     * click directly to it via JavaScript. Immune to viewport/scroll
     * coordinate bugs that make native clicks land on the wrong element.
     */
    private void clickReliably(By locator) {
        WebElement el = wait.until(ExpectedConditions.elementToBeClickable(locator));
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].scrollIntoView({block:'center'});", el);
        js.executeScript("arguments[0].click();", el);
    }

    /**
     * Set an input's value via JavaScript instead of clear()+sendKeys().
     * Same reason as clickReliably(): with an older Selenium driving a much
     * newer Chrome, typing into elements outside the original viewport can
     * silently fail (the amount field stayed empty -> the API rejected the
     * transfer with 400 VALIDATION_ERROR). Setting .value directly and firing
     * the input/change events is immune to element position.
     */
    private void setField(By locator, String value) {
        WebElement el = wait.until(ExpectedConditions.presenceOfElementLocated(locator));
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center'});" +
                        "arguments[0].value = arguments[1];" +
                        "arguments[0].dispatchEvent(new Event('input',  {bubbles: true}));" +
                        "arguments[0].dispatchEvent(new Event('change', {bubbles: true}));",
                el, value);
    }
}