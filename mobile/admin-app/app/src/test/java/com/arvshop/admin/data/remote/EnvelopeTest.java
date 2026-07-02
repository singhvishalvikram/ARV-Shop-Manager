package com.arvshop.admin.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.arvshop.admin.TestFixtures;

import org.json.JSONObject;
import org.junit.Test;

/** Universal-envelope parsing against fixtures from the live API. */
public class EnvelopeTest {

    @Test
    public void unwrapsSuccessData() throws Exception {
        Object data = Envelope.unwrap(TestFixtures.load("envelope_success.json"), 200);
        assertTrue(data instanceof JSONObject);
        assertEquals("abc123", ((JSONObject) data).optString("token"));
    }

    @Test
    public void throwsTypedErrorWithCodeAndStatus() throws Exception {
        try {
            Envelope.unwrap(TestFixtures.load("envelope_error.json"), 409);
            fail("expected ApiException");
        } catch (ApiException e) {
            assertEquals("INSUFFICIENT_STOCK", e.code);
            assertEquals(409, e.httpStatus);
            assertEquals("Not enough stock", e.getMessage());
        }
    }

    @Test
    public void treatsSuccessFalseAsError_evenWith200() throws Exception {
        String body = "{\"success\":false,\"data\":null,\"error\":{\"code\":\"X\",\"message\":\"m\"}}";
        try {
            Envelope.unwrap(body, 200);
            fail("expected ApiException");
        } catch (ApiException e) {
            assertEquals("X", e.code);
        }
    }

    @Test
    public void malformedBodyBecomesParseError() {
        try {
            Envelope.unwrap("not json", 200);
            fail("expected ApiException");
        } catch (ApiException e) {
            assertEquals(ApiException.CODE_PARSE, e.code);
        }
    }
}
