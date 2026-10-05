CREATE TABLE listing_forms (
 seller_id BIGINT NOT NULL, form_id UUID NOT NULL,
 title VARCHAR(255),description VARCHAR(2000),species VARCHAR(255),breed VARCHAR(255),age INT,price INT,city VARCHAR(255),passport_id VARCHAR(32),version BIGINT NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),PRIMARY KEY(seller_id,form_id)
);
CREATE INDEX listing_forms_updated_at_idx ON listing_forms(updated_at);
