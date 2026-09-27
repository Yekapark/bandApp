package com.yeka.bandapp.plan;

import com.yeka.bandapp.plan.service.PurchaseBandTag;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseBandTagTest {

    @Test
    void round_trips_band_id() {
        assertThat(PurchaseBandTag.of(42)).isEqualTo("band-42");
        assertThat(PurchaseBandTag.parse("band-42")).isEqualTo(OptionalLong.of(42));
    }

    @Test
    void anything_else_is_empty() {
        assertThat(PurchaseBandTag.parse(null)).isEmpty();
        assertThat(PurchaseBandTag.parse("")).isEmpty();
        assertThat(PurchaseBandTag.parse("user-42")).isEmpty();
        assertThat(PurchaseBandTag.parse("band-")).isEmpty();
        assertThat(PurchaseBandTag.parse("band-x")).isEmpty();
        assertThat(PurchaseBandTag.parse("band-0")).isEmpty();
        assertThat(PurchaseBandTag.parse("band--3")).isEmpty();
    }
}
