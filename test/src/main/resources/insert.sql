INSERT INTO vault.document MARKED ''
  ( title ) VALUES ( 'puppies' );
SET @doc1 = IDENTITY();

INSERT INTO vault.document MARKED '1/A'
  ( title ) VALUES ( 'rocks' );
SET @doc2 = IDENTITY();

INSERT INTO vault.document MARKED '2/A'
  ( title ) VALUES ( 'moon base' );
SET @doc3 = IDENTITY();

INSERT INTO vault.document MARKED '3/A'
  ( title ) VALUES ( 'moon base 2' );
SET @doc4 = IDENTITY();

INSERT INTO vault.document MARKED '3/B'
  ( title ) VALUES ( 'sun base' );
SET @doc5 = IDENTITY();

INSERT INTO vault.page
  ( doc_id, page_number, page_text ) VALUES
    ( @doc1, 1, 'abc' ),
    ( @doc1, 2, 'def' );

INSERT INTO vault.page MARKED '1/-'
  ( doc_id, page_number, page_text ) VALUES
    ( @doc1, 3, 'ghi' );
