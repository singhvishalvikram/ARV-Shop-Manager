package com.arvshop.customer.util;

import com.arvshop.customer.data.model.CartItem;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

/**
 * Builds the WhatsApp order enquiry — the same checkout model as the PWA
 * (git-pages/app.js): no order backend exists; fulfillment is human-to-human.
 * Pure Java (returns String URLs, not android.net.Uri) so it is unit-testable.
 */
public final class WhatsAppCheckout {

    private WhatsAppCheckout() { }

    /** Human-readable order message with line items and grand total. */
    public static String buildMessage(List<CartItem> items, String shopTitle,
                                      String currencySymbol) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hello ").append(shopTitle).append("! I would like to order:\n\n");
        int line = 1;
        for (CartItem it : items) {
            sb.append(line++).append(". ").append(it.name).append('\n')
              .append("   Qty: ").append(it.qty)
              .append(" × ").append(Formats.money(currencySymbol, it.price))
              .append(" = ").append(Formats.money(currencySymbol, it.lineTotal()))
              .append('\n');
        }
        sb.append("\nTotal: ")
          .append(Formats.money(currencySymbol, CartCalculator.total(items)));
        return sb.toString();
    }

    /**
     * wa.me deep link. Digits-only phone per WhatsApp URL spec
     * (settings.whatsapp_number is already stored like "917052696929").
     */
    public static String buildUrl(String whatsappNumber, String message) {
        String phone = whatsappNumber == null ? "" : whatsappNumber.replaceAll("\\D", "");
        String encoded;
        try {
            encoded = URLEncoder.encode(message, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 is always supported", e);
        }
        return "https://wa.me/" + phone + "?text=" + encoded;
    }
}
