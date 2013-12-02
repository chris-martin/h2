CREATE ROLE basic;

CREATE USER alice PASSWORD '';
GRANT basic TO alice;

CREATE USER bob PASSWORD '';
GRANT basic TO bob;

CREATE TABLE person (
  person_id IDENTITY,
  person_name VARCHAR(255)
);

CREATE SCHEMA RESTRICTED vault;

CREATE TABLE vault.document (
  doc_id     IDENTITY,
  title      VARCHAR(255),
  released   DATE,
  author_id  BIGINT
);

ALTER TABLE vault.document
  ADD FOREIGN KEY ( author_id )
  REFERENCES public.person ( person_id );

CREATE TABLE vault.page (
  doc_id       BIGINT  NOT NULL,
  page_number  INT     NOT NULL,
  page_text    CLOB
);

ALTER TABLE vault.page
  ADD PRIMARY KEY ( doc_id, page_number );

ALTER TABLE vault.page
  ADD FOREIGN KEY ( doc_id )
  REFERENCES vault.document ( doc_id );

GRANT INSERT, SELECT ON person TO basic;

GRANT INSERT, SELECT on vault.document to basic;

GRANT INSERT, SELECT on vault.page to basic;
