/*
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.google.sheets;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import com.consol.citrus.dsl.endpoint.CitrusEndpoints;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.component.google.sheets.server.GoogleSheetsApiTestServer;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IntrospectionSupport;
import org.junit.After;
import org.junit.Before;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.SocketUtils;

import static org.apache.camel.component.google.sheets.server.GoogleSheetsApiTestServerAssert.assertThatGoogleApi;

/**
 * Abstract base class for GoogleSheets Integration tests generated by Camel
 * API component maven plugin.
 */
public class AbstractGoogleSheetsTestSupport extends CamelTestSupport {

    protected static final String TEST_SHEET = "TestData";
    private static final String TEST_OPTIONS_PROPERTIES = "/test-options.properties";
    protected static final String GOOGLE_API_SERVER_KEYSTORE = "googleapis.jks";
    protected static final String GOOGLE_API_SERVER_KEYSTORE_PASSWORD = "secret";

    private Spreadsheet spreadsheet;

    private static GoogleSheetsApiTestServer googleApiTestServer;

    @Before
    public void initServer() {
        getGoogleApiTestServer().init();
    }

    @After
    public void resetServer() {
        getGoogleApiTestServer().reset();
    }

    /**
     * Create test spreadsheet that is used throughout all tests.
     */
    private void createTestSpreadsheet() {
        Spreadsheet spreadsheet = new Spreadsheet();
        SpreadsheetProperties spreadsheetProperties = new SpreadsheetProperties();
        spreadsheetProperties.setTitle("camel-sheets-" + Math.abs(new Random().nextInt()));

        spreadsheet.setProperties(spreadsheetProperties);

        Sheet sheet = new Sheet();
        SheetProperties sheetProperties = new SheetProperties();
        sheetProperties.setTitle(TEST_SHEET);
        sheet.setProperties(sheetProperties);

        spreadsheet.setSheets(Collections.singletonList(sheet));

        this.spreadsheet = (Spreadsheet) requestBody("google-sheets://spreadsheets/create?inBody=content", spreadsheet);
    }

    /**
     * Add some initial test data to test spreadsheet.
     */
    private void createTestData() {
        if (spreadsheet == null) {
            createTestSpreadsheet();
        }

        ValueRange valueRange = new ValueRange();
        valueRange.setValues(Arrays.asList(Arrays.asList("a1", "b1"), Arrays.asList("a2", "b2")));

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleSheets.spreadsheetId", spreadsheet.getSpreadsheetId());
        // parameter type is String
        headers.put("CamelGoogleSheets.range", TEST_SHEET + "!A1:B2");

        // parameter type is String
        headers.put("CamelGoogleSheets.valueInputOption", "USER_ENTERED");

        requestBodyAndHeaders("google-sheets://data/update?inBody=values", valueRange, headers);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {

        final CamelContext context = super.createCamelContext();

        final GoogleSheetsConfiguration configuration = new GoogleSheetsConfiguration();
        IntrospectionSupport.setProperties(configuration, getTestOptions());

        // add GoogleSheetsComponent to Camel context and use localhost url
        final GoogleSheetsComponent component = new GoogleSheetsComponent(context);
        component.setClientFactory(new BatchGoogleSheetsClientFactory(
                                            new NetHttpTransport.Builder()
                                                    .trustCertificatesFromJavaKeyStore(
                                                            getClass().getResourceAsStream("/" + GOOGLE_API_SERVER_KEYSTORE),
                                                            GOOGLE_API_SERVER_KEYSTORE_PASSWORD)
                                                    .build(),
                                            new JacksonFactory()) {
            @Override
            protected void configure(Sheets.Builder clientBuilder) {
                clientBuilder.setRootUrl(String.format("https://localhost:%s/", getGoogleApiTestServer().getHttpServer().getPort()));
            }
        });
        component.setConfiguration(configuration);
        context.addComponent("google-sheets", component);

        return context;
    }

    /**
     * Read component configuration from TEST_OPTIONS_PROPERTIES.
     * @return Map of component options.
     * @throws IOException when TEST_OPTIONS_PROPERTIES could not be loaded.
     */
    protected Map<String, Object> getTestOptions() throws IOException {
        final Properties properties = new Properties();
        try {
            properties.load(getClass().getResourceAsStream(TEST_OPTIONS_PROPERTIES));
        } catch (Exception e) {
            throw new IOException(String.format("%s could not be loaded: %s", TEST_OPTIONS_PROPERTIES, e.getMessage()), e);
        }

        Map<String, Object> options = new HashMap<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            options.put(entry.getKey().toString(), entry.getValue());
        }

        return options;
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        // only create the context once for this class
        return true;
    }

    @SuppressWarnings("unchecked")
    protected Object requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers) throws CamelExecutionException {
        return template().requestBodyAndHeaders(endpointUri, body, headers);
    }

    @SuppressWarnings("unchecked")
    protected Object requestBody(String endpoint, Object body) throws CamelExecutionException {
        return template().requestBody(endpoint, body);
    }

    public Spreadsheet getSpreadsheet() {
        if (spreadsheet == null) {
            createTestSpreadsheet();
        }
        return spreadsheet;
    }

    public Spreadsheet getSpreadsheetWithTestData() {
        if (spreadsheet == null) {
            createTestSpreadsheet();
        }

        createTestData();

        return spreadsheet;
    }

    public void setSpreadsheet(Spreadsheet sheet) {
        this.spreadsheet = sheet;
    }

    public GoogleSheetsApiTestServer getGoogleApiTestServer() {
        if (googleApiTestServer == null) {
            try {
                Map<String, Object> testOptions = getTestOptions();
                int serverPort = SocketUtils.findAvailableTcpPort();
                googleApiTestServer = new GoogleSheetsApiTestServer.Builder(CitrusEndpoints.http()
                        .server()
                        .port(serverPort)
                        .timeout(5000)
                        .autoStart(true))
                        .keyStorePath(new ClassPathResource(GOOGLE_API_SERVER_KEYSTORE).getFile().toPath())
                        .keyStorePassword(GOOGLE_API_SERVER_KEYSTORE_PASSWORD)
                        .securePort(serverPort)
                        .clientId(testOptions.get("clientId").toString())
                        .clientSecret(testOptions.get("clientSecret").toString())
                        .accessToken(testOptions.get("accessToken").toString())
                        .refreshToken(testOptions.get("refreshToken").toString())
                        .build();

                assertThatGoogleApi(googleApiTestServer).isRunning();
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }

        return googleApiTestServer;
    }

}
