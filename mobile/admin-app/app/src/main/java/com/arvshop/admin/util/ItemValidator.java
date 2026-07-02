package com.arvshop.admin.util;

/**
 * Client-side form validation that mirrors the server's Pydantic constraints
 * (schemas.py). This is UX-only; the server remains the authority (GUARDRAILS 2.1).
 * Pure logic — unit-tested in ItemValidatorTest.
 */
public final class ItemValidator {

    public static final class Errors {
        public String name;
        public String type;
        public String price;
        public String quantity;

        public boolean hasErrors() {
            return name != null || type != null || price != null || quantity != null;
        }
    }

    private ItemValidator() { }

    public static Errors validate(String name, String type, String priceText, String qtyText) {
        Errors e = new Errors();
        if (name == null || name.trim().isEmpty()) {
            e.name = "Name is required";
        } else if (name.trim().length() > 200) {
            e.name = "Name is too long (max 200)";
        }
        if (type == null || type.trim().isEmpty()) {
            e.type = "Category is required";
        }
        Double price = parsePositiveDouble(priceText);
        if (price == null || price <= 0) {
            e.price = "Enter a price greater than 0";
        }
        Integer qty = parseNonNegativeInt(qtyText);
        if (qty == null) {
            e.quantity = "Enter a quantity (0 or more)";
        }
        return e;
    }

    public static Double parsePositiveDouble(String text) {
        if (text == null) return null;
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Integer parseNonNegativeInt(String text) {
        if (text == null) return null;
        try {
            int v = Integer.parseInt(text.trim());
            return v >= 0 ? v : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
