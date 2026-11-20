package com.signasource.signa_api.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CopyCodeControllerTest {

    @Test
    void servesAStaticPageThatReadsTheCodeFromTheFragmentOnly() {
        String page = new CopyCodeController().copyCode();

        assertThat(page)
                .contains("/^#c=(\\d{6})$/.exec(location.hash)")
                .contains("navigator.clipboard.writeText")
                .contains("textContent")
                .doesNotContain("innerHTML")
                .doesNotContain("location.search");
    }
}
