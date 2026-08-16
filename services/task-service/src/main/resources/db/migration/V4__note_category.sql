ALTER TABLE note
    ADD COLUMN category_id BINARY(16) NULL,
    ADD INDEX idx_note_category (category_id),
    ADD CONSTRAINT fk_note_category
        FOREIGN KEY (category_id) REFERENCES category (id)
        ON DELETE SET NULL;
