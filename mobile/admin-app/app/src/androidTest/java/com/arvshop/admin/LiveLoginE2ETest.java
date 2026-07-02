package com.arvshop.admin;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.arvshop.admin.core.ServiceLocator;
import com.arvshop.admin.ui.auth.LoginActivity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * End-to-end login against the REAL backend (dev flavor → 10.0.2.2:8000).
 * Requires the FastAPI server running with the seeded demo owner
 * (phone 9999999999 / owner1234). Drives the actual /api/v1/auth/login call and
 * verifies the app advances to the dashboard (its SwipeRefresh view is shown).
 *
 * <p>Network is asynchronous and Espresso does not auto-wait for it, so the test
 * polls for the dashboard view for a bounded time.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class LiveLoginE2ETest {

    @Rule
    public ActivityScenarioRule<LoginActivity> rule =
            new ActivityScenarioRule<>(LoginActivity.class);

    @Before
    public void clearSession() {
        ServiceLocator.init(ApplicationProvider.getApplicationContext());
        ServiceLocator.session().clear();
    }

    @Test
    public void login_withSeededOwner_reachesDashboard() throws InterruptedException {
        onView(withId(R.id.login_phone)).perform(replaceText("9999999999"), closeSoftKeyboard());
        onView(withId(R.id.login_password)).perform(replaceText("owner1234"), closeSoftKeyboard());
        onView(withId(R.id.login_button)).perform(click());

        // Poll for the dashboard (login round-trips to the backend).
        boolean reached = false;
        for (int i = 0; i < 20 && !reached; i++) {
            Thread.sleep(500);
            try {
                onView(withId(R.id.swipe_refresh)).check(matches(isDisplayed()));
                reached = true;
            } catch (Throwable ignored) {
                // not there yet — keep polling
            }
        }
        onView(withId(R.id.swipe_refresh)).check(matches(isDisplayed()));
    }
}
