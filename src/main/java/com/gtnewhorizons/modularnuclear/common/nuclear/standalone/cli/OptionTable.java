package com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Named options applied to a context, declared once and read either from command-line arguments
 * ({@code --name value}, {@code --flag}, {@code --no-flag}) or from {@code key=value} tokens. Names match without
 * regard to case, leading dashes, dashes or underscores, so {@code --fission-heat}, {@code fission_heat} and
 * {@code fissionheat} are one option.
 *
 * @param <C> what the options set
 */
public final class OptionTable<C> {

    /** Reads an option's value; throws an IllegalArgumentException (or NumberFormatException) if it is malformed. */
    public interface Parser<T> {

        T parse(String text);
    }

    public static final Parser<Integer> INT = s -> Integer.parseInt(s.trim());
    public static final Parser<Long> LONG = s -> Long.parseLong(s.trim());
    public static final Parser<Double> DOUBLE = s -> Double.parseDouble(s.trim());
    public static final Parser<String> STRING = s -> s;
    /** "true" (any case) or "1". */
    public static final Parser<Boolean> BOOL = s -> "1".equals(s.trim()) || "true".equalsIgnoreCase(s.trim());

    private enum Arity {
        /** Always takes a value. */
        VALUE,
        /** On the command line, takes the next argument only if it is not another option. */
        OPTIONAL_VALUE,
        /** A switch: --name sets it, --no-name clears it; as a token, name=true or name=false. */
        TOGGLE
    }

    private static final class Option<C> {

        final Arity arity;
        final BiConsumer<C, String> action;

        Option(Arity arity, BiConsumer<C, String> action) {
            this.arity = arity;
            this.action = action;
        }
    }

    private final Map<String, Option<C>> options = new HashMap<>();
    private final List<String> declared = new ArrayList<>();
    private BiConsumer<String, String> onBadValue = (name, value) -> System.err
        .println("WARNING: bad value for option " + name + ": " + value);
    private Consumer<String> onUnknown = name -> {};

    /** The form names are matched in: lower case, without leading dashes, dashes or underscores. */
    public static String normalize(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != '-' && c != '_') sb.append(Character.toLowerCase(c));
        }
        return sb.toString()
            .toLowerCase(Locale.ROOT);
    }

    private OptionTable<C> add(Arity arity, BiConsumer<C, String> action, String... names) {
        for (String n : names) {
            if (options.put(normalize(n), new Option<>(arity, action)) != null) {
                throw new IllegalStateException("option declared twice: " + n);
            }
            declared.add(n);
        }
        return this;
    }

    /** An option with a value. */
    public <T> OptionTable<C> value(Parser<T> parser, BiConsumer<C, T> set, String... names) {
        return add(Arity.VALUE, (c, v) -> set.accept(c, parser.parse(v)), names);
    }

    /** An option whose value may be left out on the command line ({@code --trace} or {@code --trace 20}). */
    public <T> OptionTable<C> optionalValue(Parser<T> parser, Consumer<C> present, BiConsumer<C, T> set,
        String... names) {
        return add(Arity.OPTIONAL_VALUE, (c, v) -> {
            present.accept(c);
            if (v != null) set.accept(c, parser.parse(v));
        }, names);
    }

    /** A boolean switch. */
    public OptionTable<C> toggle(BiConsumer<C, Boolean> set, String... names) {
        return add(Arity.TOGGLE, (c, v) -> set.accept(c, v == null || BOOL.parse(v)), names);
    }

    /** An option that takes no value and does something (as a token, its value is ignored). */
    public OptionTable<C> action(Consumer<C> run, String... names) {
        return add(Arity.OPTIONAL_VALUE, (c, v) -> run.accept(c), names);
    }

    /** What to do with a malformed value (by default, warn on stderr). */
    public OptionTable<C> onBadValue(BiConsumer<String, String> handler) {
        this.onBadValue = handler;
        return this;
    }

    /** What to do with an option that is not declared (by default, nothing). */
    public OptionTable<C> onUnknown(Consumer<String> handler) {
        this.onUnknown = handler;
        return this;
    }

    public boolean has(String name) {
        return options.containsKey(normalize(name));
    }

    /** The names options were declared under, in declaration order. */
    public List<String> names() {
        return Collections.unmodifiableList(declared);
    }

    /** Applies one option; a malformed value is reported and leaves the context as it was. */
    public void apply(C context, String name, String value) {
        Option<C> o = options.get(normalize(name));
        if (o == null) {
            onUnknown.accept(name);
            return;
        }
        try {
            o.action.accept(context, value);
        } catch (IllegalArgumentException e) {
            onBadValue.accept(name, value);
        }
    }

    /** Applies command-line arguments in order. */
    public void parseArgs(C context, String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("-")) continue;
            String key = normalize(arg);
            Option<C> o = options.get(key);
            if (o == null && key.startsWith("no")
                && options.containsKey(key.substring(2))
                && options.get(key.substring(2)).arity == Arity.TOGGLE) {
                options.get(key.substring(2)).action.accept(context, "false");
                continue;
            }
            if (o == null) {
                onUnknown.accept(arg);
                continue;
            }
            String value = null;
            boolean hasNext = i + 1 < args.length;
            if (o.arity == Arity.VALUE) {
                if (!hasNext) continue;
                value = args[++i];
            } else if (o.arity == Arity.OPTIONAL_VALUE && hasNext && !args[i + 1].startsWith("-")) {
                value = args[++i];
            }
            try {
                o.action.accept(context, value);
            } catch (IllegalArgumentException e) {
                onBadValue.accept(arg, value);
            }
        }
    }

    /** Applies the {@code key=value} tokens of a line (separated by the given regex) in order. */
    public void parseTokens(C context, String line, String separatorRegex) {
        for (String token : line.split(separatorRegex)) {
            int eq = token.indexOf('=');
            if (eq <= 0) continue;
            apply(
                context,
                token.substring(0, eq)
                    .trim(),
                token.substring(eq + 1)
                    .trim());
        }
    }
}
