package com.arvshop.customer.data.model;

import java.util.Collections;
import java.util.Map;

/**
 * Mirrors git-pages/data/settings.json — a FLAT map of strings where booleans
 * are encoded as "0"/"1". Typed accessors keep the UI honest about defaults.
 * White-label rule (AGENTS.md): never hardcode shop branding — read it from here.
 */
public final class ShopSettings {

    private final Map<String, String> raw;

    public ShopSettings(Map<String, String> raw) {
        this.raw = Collections.unmodifiableMap(raw);
    }

    private String str(String key, String fallback) {
        String v = raw.get(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    private boolean flag(String key, boolean fallback) {
        String v = raw.get(key);
        if (v == null) return fallback;
        return v.equals("1") || v.equalsIgnoreCase("true");
    }

    public String appTitle()           { return str("app_title", "Shop"); }
    public String appSubtitle()        { return str("app_subtitle", ""); }
    public String whatsappNumber()     { return str("whatsapp_number", ""); }
    public String currencySymbol()     { return str("currency_symbol", "₹"); }
    public String shopLocation()       { return str("shop_location", ""); }
    public String footerText()         { return str("footer_text", ""); }

    public boolean showSearch()        { return flag("show_search", true); }
    public boolean showCategoryFilter(){ return flag("show_category_filter", true); }
    public boolean showDiscountBadges(){ return flag("show_discount_badges", true); }
    public boolean showMrp()           { return flag("show_mrp", true); }
    public boolean showDescription()   { return flag("show_description", true); }
    public boolean showImages()        { return flag("show_images", true); }

    public int maxProducts() {
        try {
            return Integer.parseInt(str("max_products", "0"));
        } catch (NumberFormatException e) {
            return 0; // 0 = unlimited, matching the PWA behavior
        }
    }
}
