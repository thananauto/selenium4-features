package com.features.reuse;

import kong.unirest.Unirest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.NetworkInterceptor;
import org.openqa.selenium.devtools.v126.fetch.Fetch;
import org.openqa.selenium.devtools.v126.fetch.model.HeaderEntry;
import org.openqa.selenium.remote.http.*;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class NetworkApiMock {
    WebDriver driver;
    WebDriverWait wait;

    @BeforeEach
    public void setUp() {
        driver = new ChromeDriver();
        wait = new WebDriverWait(driver, Duration.ofSeconds(60));

    }

    @AfterEach
    public void tearDown() {
        Optional.of(driver).ifPresent(WebDriver::quit);
    }

    @Test
    public void mockAPIByCdp() {
        AtomicBoolean completed = new AtomicBoolean(false);
        //response from mock server for endpoint '/brands'
        Supplier<String> mockBody = () -> Unirest.get("http://localhost:3001/brands").asString().getBody();
        //enable devtools to access all features
        DevTools devTools = ((ChromeDriver) driver).getDevTools();
        devTools.createSession();
        devTools.send(Fetch.enable(Optional.empty(), Optional.empty()));
       //all request is paused, Refer here: https://chromedevtools.github.io/devtools-protocol/tot/Fetch/
        devTools.addListener(Fetch.requestPaused(), (req) -> {
            if (req.getRequest().getMethod().equalsIgnoreCase("get") && req.getRequest().getUrl().contains("categories/tree")) {
                String body = Base64.getEncoder().encodeToString(mockBody.get().getBytes());
                List<HeaderEntry> headerEntries = new ArrayList<>();
                headerEntries.add(new HeaderEntry("Access-Control-Allow-Origin", "*"));
                //fullfill the same request with mocked body response data
                devTools.send(Fetch.fulfillRequest(req.getRequestId(), 200, Optional.of(headerEntries),
                        Optional.empty(), Optional.of(body), Optional.of("No Value present")));
                completed.set(true);
            } else {
                //continue with rest of the request url
                devTools.send(Fetch.continueRequest(req.getRequestId(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            }
        });

        driver.get("https://practicesoftwaretesting.com/");
        wait.until(d -> completed.get());// wait till mock response successfully executes
        System.out.println(driver.getTitle());
    }

    @Test
    public void mockApiRoute() {
        AtomicBoolean completed = new AtomicBoolean(false);
        //response from mock server for endpoint '/brands'
        Supplier<String> mockRes = () -> Unirest.get("http://localhost:3001/brands").asString().getBody();
        String mockBody = mockRes.get();

        //route only match to method: GET with url "categories/tree"
        Routable routable = Route.matching(req -> req.getMethod().equals(HttpMethod.GET) && req.getUri().contains("categories/tree"))
                .to(() -> req -> {
                    completed.set(true);
                    return new HttpResponse()
                        .setStatus(200)
                        .setHeader("Access-Control-Allow-Origin", "*")
                        .setHeader("Content-Type", "application/json;charset=UTF-8")
                        .setContent(Contents.utf8String(mockBody));
                });
        try (NetworkInterceptor ns = new NetworkInterceptor(driver,
                routable)) {
            driver.get("https://practicesoftwaretesting.com/");
            wait.until(d-> completed.get());
        }
        System.out.println(driver.getTitle());
    }

    @Test
    public void mockApiFilter() {
        AtomicBoolean completed = new AtomicBoolean(false);

        Supplier<String> mockRes = () -> Unirest.get("http://localhost:3001/brands").asString().getBody();
        String mockBody = mockRes.get();
        Filter filter = httpHandler -> (HttpHandler) req -> {
            if (req.getMethod().equals(HttpMethod.GET) && req.getUri().contains("categories/tree")) {
                HttpResponse response = httpHandler.execute(req);
                response.setContent(Contents.utf8String(mockBody));
                completed.set(true);
                return response;
            } else {
                return httpHandler.execute(req);
            }
        };

        try (NetworkInterceptor ns = new NetworkInterceptor(driver,
                filter)) {
            driver.get("https://practicesoftwaretesting.com/");
            wait.until(d -> completed.get());

        }
        System.out.println(driver.getTitle());
    }


}
