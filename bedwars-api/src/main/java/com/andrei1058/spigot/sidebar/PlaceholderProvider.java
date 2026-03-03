package com.andrei1058.spigot.sidebar;

import java.util.Objects;
import java.util.function.Supplier;

public class PlaceholderProvider {

    private final String placeholder;
    private final Supplier<String> valueProvider;

    public PlaceholderProvider(String placeholder, Supplier<String> valueProvider) {
        this.placeholder = Objects.requireNonNull(placeholder, "placeholder");
        this.valueProvider = Objects.requireNonNull(valueProvider, "valueProvider");
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public String getValue() {
        return valueProvider.get();
    }
}

