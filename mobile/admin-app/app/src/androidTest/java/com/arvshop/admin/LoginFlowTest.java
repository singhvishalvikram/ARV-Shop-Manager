package com.arvshop.admin;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.intent.Intents;
import androidx.test.espresso.intent.matcher.IntentMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.arvshop.admin.core.ServiceLocator;
import com.arvshop.admin.ui.auth.LoginActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Espresso UI test for the login screen. Network-independent: it verifies the
 * screen renders, client-side validation fires on empty submit, and the "create
 * account" link navigates to signup. This proves the UI wiring + instrumentation
 * harness without depending on a live backend or emulator connectivity.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class LoginFlowTest {

    @Rule
    public ActivityScenarioRule<LoginActivity> rule =
            new ActivityScenarioRule<>(LoginActivity.class);

    @Before
    public void clearSession() {
        // Ensure we land on the login screen (not auto-forwarded to dashboard).
        ServiceLocator.init(ApplicationProvider.getApplicationContext());
        ServiceLocator.session().clear();
    }

    @Test
    public void loginScreen_rendersFields() {
        onView(withId(R.id.login_phone)).check(matches(isDisplayed()));
        onView(withId(R.id.login_password)).check(matches(isDisplayed()));
        onView(withId(R.id.login_button)).check(matches(isDisplayed()));
    }

    @Test
    public void emptySubmit_showsValidationError() {
        onView(withId(R.id.login_phone)).perform(clearText(), closeSoftKeyboard());
        onView(withId(R.id.login_password)).perform(clearText(), closeSoftKeyboard());
        onView(withId(R.id.login_button)).perform(click());
        onView(withId(R.id.login_error)).check(matches(isDisplayed()));
    }

    @Test
    public void createAccountLink_navigatesToSignup() {
        Intents.init();
        try {
            onView(withId(R.id.go_to_signup)).perform(click());
            Intents.intended(IntentMatchers.hasComponent(
                    "com.arvshop.admin.ui.auth.SignupActivity"));
        } finally {
            Intents.release();
        }
    }
}
