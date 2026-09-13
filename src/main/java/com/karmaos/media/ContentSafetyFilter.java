package com.karmaos.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class ContentSafetyFilter {

    private static final Logger log = LoggerFactory.getLogger(ContentSafetyFilter.class);

    private ContentSafetyFilter() {
    }

    private static final List<String[]> REPLACEMENTS = new ArrayList<>();
    private static final Map<String, String> ORDERED = new LinkedHashMap<>();
    static {
        ORDERED.put("burning alive", "playing happily");
        ORDERED.put("dead body", "sleeping character");
        ORDERED.put("pornography", "playful cartoon");
        ORDERED.put("weapons", "friendly objects");
        ORDERED.put("weaponry", "friendly objects");
        ORDERED.put("soldiers", "brave helpers");
        ORDERED.put("military", "team of friends");
        ORDERED.put("ammunition", "confetti");
        ORDERED.put("bullets", "confetti");
        ORDERED.put("cigarettes", "candies");
        ORDERED.put("alcohol", "juice");
        ORDERED.put("heroin", "candy");
        ORDERED.put("cocaine", "candy");
        ORDERED.put("whiskey", "juice");
        ORDERED.put("torture", "teamwork");
        ORDERED.put("tortured", "challenged");
        ORDERED.put("whipping", "playing");
        ORDERED.put("slaughter", "adventure");
        ORDERED.put("killing", "helping");
        ORDERED.put("killed", "helped");
        ORDERED.put("kill", "help");
        ORDERED.put("murder", "adventure");
        ORDERED.put("explosion", "celebration");
        ORDERED.put("explosive", "festive");
        ORDERED.put("decapitated", "cartoon");
        ORDERED.put("cannibal", "cartoon");
        ORDERED.put("severed", "separate");
        ORDERED.put("corpse", "character");
        ORDERED.put("carcass", "cartoon");
        ORDERED.put("skeleton", "cartoon character");
        ORDERED.put("skeletons", "cartoon characters");
        ORDERED.put("porn", "playful cartoon");
        ORDERED.put("nsfw", "family friendly");
        ORDERED.put("explicit", "wholesome");
        ORDERED.put("nude", "fully clothed cartoon");
        ORDERED.put("naked", "fully clothed cartoon");
        ORDERED.put("nudity", "fully clothed cartoon");
        ORDERED.put("topless", "fully clothed cartoon");
        ORDERED.put("erotic", "cartoon");
        ORDERED.put("sexual", "friendly");
        ORDERED.put("sexy", "friendly");
        ORDERED.put("sex", "friendship");
        ORDERED.put("xxx", "cartoon");
        ORDERED.put("bloodshed", "friendly gesture");
        ORDERED.put("blood", "friendly gesture");
        ORDERED.put("gore", "cartoon");
        ORDERED.put("wounded", "tired");
        ORDERED.put("wound", "boo-boo");
        ORDERED.put("weapon", "friendly object");
        ORDERED.put("rifle", "friendly object");
        ORDERED.put("pistol", "friendly object");
        ORDERED.put("firearm", "friendly object");
        ORDERED.put("sword", "friendly stick");
        ORDERED.put("knife", "friendly stick");
        ORDERED.put("axe", "friendly tool");
        ORDERED.put("bomb", "balloon");
        ORDERED.put("tank", "friendly vehicle");
        ORDERED.put("bullet", "confetti");
        ORDERED.put("whip", "friendly rope");
        ORDERED.put("gun", "friendly object");
        ORDERED.put("guns", "friendly objects");
        ORDERED.put("attack", "approach");
        ORDERED.put("attacking", "approaching");
        ORDERED.put("warfare", "adventure");
        ORDERED.put("war", "adventure");
        ORDERED.put("battle", "friendly game");
        ORDERED.put("soldier", "brave helper");
        ORDERED.put("army", "group of friends");
        ORDERED.put("drug", "treat");
        ORDERED.put("drugs", "treats");
        ORDERED.put("smoking", "playing");
        ORDERED.put("cigarette", "candy");
        ORDERED.put("beer", "juice");
    }

    static {
        for (Map.Entry<String, String> e : ORDERED.entrySet()) {
            REPLACEMENTS.add(new String[]{e.getKey(), e.getValue()});
        }
    }

    private static final List<String> SAFETY_SUFFIXES = List.of(
        "Safe, wholesome, family-friendly children's storybook illustration.",
        "No violence, no weapons, no blood, no gore, no war, no adult content, no nudity.",
        "Bright, cheerful, soft, warm, age-appropriate cartoon style for all audiences."
    );

    private static final List<String> BLOCKED_PHRASES = List.of(
        "nude", "naked", "topless", "porn", "nsfw", "erotic", "sexual",
        "gore", "corpse", "murder", "sexy", "explicit", "xxx"
    );

    public static String sanitizeImagePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "A clean, minimal, professional diagram for a technical explainer.";
        }
        return prompt.trim();
    }

    public static boolean containsBlockedContent(String prompt) {
        if (prompt == null) return false;
        String lower = prompt.toLowerCase(Locale.ROOT);
        for (String phrase : BLOCKED_PHRASES) {
            if (lower.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private static String replaceWord(String input, String search, String replacement) {
        if (input == null || search == null || search.isBlank()) return input;
        if (!input.toLowerCase(Locale.ROOT).contains(search)) return input;
        String pattern = "(?i)\\b" + Pattern.quote(search) + "\\b";
        try {
            return input.replaceAll(pattern, replacement);
        } catch (Exception e) {
            return input.replace(search, replacement);
        }
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }
}
