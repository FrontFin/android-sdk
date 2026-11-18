package com.meshpay.link;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Locks how Java apps use the public API: this file stops compiling if the Java
 * path to {@link LinkOptions} or {@link LinkConfiguration} regresses.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class LinkOptionsJavaTest {
    @Test
    public void builderChainsFromJava() {
        LinkOptions options = new LinkOptions.Builder()
                .environment(LinkEnvironment.SBX)
                .language("es")
                .theme(LinkTheme.DARK)
                .integrationId("int-1")
                .displayFiatCurrency("DKK")
                .build();

        assertEquals(LinkEnvironment.SBX, options.getEnvironment());
        assertEquals("es", options.getLanguage());
        assertEquals(LinkTheme.DARK, options.getTheme());
        assertEquals("int-1", options.getIntegrationId());
        assertEquals("DKK", options.getDisplayFiatCurrency());
    }

    @Test
    public void emptyBuilderLeavesEveryOptionUnset() {
        LinkOptions options = new LinkOptions.Builder().build();

        assertNull(options.getEnvironment());
        assertNull(options.getLanguage());
        assertNull(options.getTheme());
        assertNull(options.getIntegrationId());
        assertNull(options.getDisplayFiatCurrency());
    }

    @Test
    public void configurationTakesBuiltOptions() {
        LinkOptions options = new LinkOptions.Builder().environment(LinkEnvironment.SBX).build();

        LinkConfiguration config = new LinkConfiguration("tok", "cid", options);
        assertEquals(options, config.getOptions());
        assertEquals(new LinkOptions.Builder().build(), new LinkConfiguration("tok", "cid").getOptions());
    }
}
