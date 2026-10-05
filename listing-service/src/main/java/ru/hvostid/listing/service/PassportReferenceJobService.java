package ru.hvostid.listing.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PassportReferenceJobService {
    private final JdbcClient jdbc;

    public PassportReferenceJobService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long prepare(long listing, long passport, long seller) {
        return enqueue(listing, passport, seller);
    }

    @Transactional
    public long enqueue(long listing, long passport, long seller) {
        return jdbc.sql(
                        "INSERT INTO passport_reference_jobs(listing_id,passport_id,seller_id,available_at) VALUES(:listing,:passport,:seller,NOW()+INTERVAL '10 seconds') RETURNING id")
                .param("listing", listing)
                .param("passport", passport)
                .param("seller", seller)
                .query(Long.class)
                .single();
    }
}
