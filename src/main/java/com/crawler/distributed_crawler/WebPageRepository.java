package com.crawler.distributed_crawler;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WebPageRepository extends MongoRepository<WebPageDocument, String> {

    Optional<WebPageDocument> findByUrl(String url);

    @Query("{ 'wordFrequencies.?0' : { $exists: true } }")
    List<WebPageDocument> findByKeyword(String keyword);
}
