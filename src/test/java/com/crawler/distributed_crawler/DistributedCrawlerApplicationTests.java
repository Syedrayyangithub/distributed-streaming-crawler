package com.crawler.distributed_crawler;

import org.junit.jupiter.api.Test;

class DistributedCrawlerApplicationTests {

    @Test
    void queryHelpersLoad() {
        // Full Spring context is not started here because it needs Mongo, Redis, and Ollama.
        QueryRelevance.distinctiveTerms("laptop cooling");
    }
}
