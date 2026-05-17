package ru.hvostid.listing;

public final class ListingConstants {
    private ListingConstants() {}

    public static final String PRICE_ASC = "price_asc";
    public static final String PRICE_DESC = "price_desc";
    public static final String CREATED_DESC = "created_desc";

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public static final int MAX_SEARCH_RESULTS = 1000;

    public static final int MAX_AGE_MONTHS = 5000;
    public static final int MAX_PRICE = 999_999_999;
    public static final int MIN_PRICE = 0;
    public static final int MIN_AGE = 0;
}
