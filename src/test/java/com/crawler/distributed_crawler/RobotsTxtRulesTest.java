package com.crawler.distributed_crawler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RobotsTxtRulesTest {

    @Test
    void honorsWildcardDisallowPrefixes() {
        String robots = """
                User-agent: *
                Disallow: /wiki/Special:
                Disallow: /w/
                
                User-agent: BadBot
                Disallow: /
                """;
        RobotsTxtRules rules = RobotsTxtRules.parse(robots, "CrawlrBot");
        assertTrue(rules.allows("https://en.wikipedia.org/wiki/Sleep"));
        assertFalse(rules.allows("https://en.wikipedia.org/wiki/Special:Random"));
        assertFalse(rules.allows("https://en.wikipedia.org/w/index.php?title=Sleep"));
    }

    @Test
    void emptyOrMissingFileAllowsCrawling() {
        assertTrue(RobotsTxtRules.parse("", "CrawlrBot").allows("https://example.com/page"));
        assertTrue(RobotsTxtRules.allowAll().allows("https://example.com/page"));
    }
}
