package com.banking.tests.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Page Object Model (POM) for the LOGIN page.
 *
 * POM = one class per page. All locators live HERE, in one place.
 * If a locator changes, we fix it once — every test that uses it is fixed too.
 */
public class LoginPage {

    private final WebDriver driver;
    private final WebDriverWait wait;

    // ---------- Locators (kept private — tests never touch By directly) ----------
    private final By usernameField = By.id("username");
    private final By passwordField = By.id("password");
    private final By loginButton   = By.id("login-btn");
    private final By errorMessage  = By.id("login-error");

    public LoginPage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    /** Open the banking UI login page */
    public LoginPage open(String baseUrl) {
        driver.get(baseUrl + "/ui/index.html");
        return this;
    }

    /** Type credentials and click Log In */
    public void login(String username, String password) {
        driver.findElement(usernameField).clear();
        driver.findElement(usernameField).sendKeys(username);
        driver.findElement(passwordField).clear();
        driver.findElement(passwordField).sendKeys(password);
        driver.findElement(loginButton).click();
    }

    /** Wait for and return the red error message text (for negative tests) */
    public String getErrorMessage() {
        wait.until(d -> !d.findElement(errorMessage).getText().isBlank());
        return driver.findElement(errorMessage).getText();
    }
}
