package com.crawler.distributed_crawler;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface WebPageRepository extends MongoRepository<WebPageDocument, String> {

    // Custom database Query: Finds documents where the word_frequencies map contains our keyword
    @Query("{ 'word_frequencies.?0' : { $exists: true } }")
    List<WebPageDocument> findByKeyword(String keyword);
}