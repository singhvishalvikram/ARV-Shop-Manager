package com.arvshop.admin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/** Loads JSON fixtures captured from the live /api/v1 backend. */
public final class TestFixtures {

    private TestFixtures() { }

    public static String load(String name) throws IOException {
        try (InputStream in = TestFixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) throw new IOException("missing fixture " + name);
            try (Scanner s = new Scanner(in, StandardCharsets.UTF_8.name())) {
                return s.useDelimiter("\\A").next();
            }
        }
    }
}
