package com.yeka.bandapp.plan;

import com.yeka.bandapp.plan.config.StoreBillingProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoreBillingPropertiesTest {

    private static StoreBillingProperties withProducts(List<String> ids) {
        return new StoreBillingProperties("noop", null, null, ids, null, null, null, null);
    }

    @Test
    void defaults_to_the_five_premium_slots() {
        StoreBillingProperties p = withProducts(null);

        assertThat(p.googleProductIds()).isEqualTo(StoreBillingProperties.DEFAULT_GOOGLE_PRODUCT_IDS);
        assertThat(p.sellsGoogleProduct("premium_yearly")).isTrue();
        assertThat(p.sellsGoogleProduct("premium_yearly_5")).isTrue();
        assertThat(p.sellsGoogleProduct("premium_yearly_6")).isFalse();
        assertThat(p.sellsGoogleProduct(null)).isFalse();
    }

    @Test
    void configured_list_is_trimmed_and_blank_entries_dropped() {
        StoreBillingProperties p = withProducts(List.of(" premium_yearly ", "", "premium_yearly_2"));

        assertThat(p.googleProductIds()).containsExactly("premium_yearly", "premium_yearly_2");
        assertThat(p.sellsGoogleProduct("premium_yearly_3")).isFalse();
    }
}
