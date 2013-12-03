import java.sql.{Connection, DriverManager}
import org.jooq.SQLDialect
import org.jooq.impl.DSL.{using => createJooqContext}
import scala.collection.mutable
import scala.util.Random

object Test extends testing.Benchmark {

  Class.forName("org.h2.Driver")

  val restricted = true

  def run() = {

    val filename = s"db-${new java.util.Date().getTime}"

    implicit val random: Random = new Random

    val connections = new mutable.ArrayBuffer[Connection]()

    try {
      {
        val connection = DriverManager.getConnection(s"jdbc:h2:$filename")
        connections += connection
        val jooq = createJooqContext(connection, SQLDialect.H2)
        jooq execute s"""
          |CREATE ROLE basic;
          |
          |CREATE USER alice PASSWORD '';
          |GRANT basic TO alice;
          |
          |CREATE USER bob PASSWORD '';
          |GRANT basic TO bob;
          |
          |CREATE TABLE person (
          |  person_id IDENTITY,
          |  person_name VARCHAR(255)
          |);
          |
          |CREATE SCHEMA ${if (restricted) "RESTRICTED" else ""} vault;
          |
          |CREATE TABLE vault.document (
          |  doc_id     IDENTITY,
          |  title      VARCHAR(255),
          |  released   DATE,
          |  author_id  BIGINT
          |);
          |
          |ALTER TABLE vault.document
          |  ADD FOREIGN KEY ( author_id )
          |  REFERENCES public.person ( person_id );
          |
          |CREATE TABLE vault.page (
          |  doc_id       BIGINT  NOT NULL,
          |  page_number  INT     NOT NULL,
          |  page_text    CLOB
          |);
          |
          |ALTER TABLE vault.page
          |  ADD PRIMARY KEY ( doc_id, page_number );
          |
          |ALTER TABLE vault.page
          |  ADD FOREIGN KEY ( doc_id )
          |  REFERENCES vault.document ( doc_id );
          |
          |GRANT INSERT, SELECT ON person TO basic;
          |
          |GRANT INSERT, SELECT on vault.document to basic;
          |
          |GRANT INSERT, SELECT on vault.page to basic;
        """.stripMargin

        for {
          marking <- ('A' to 'G').map(x => s"1/$x") ++ ('A' to 'G').map(x => s"2/$x") :+ "3/-"
        } jooq.execute("""grant marking ? TO alice;""", marking)
      }
      {
        val connection = DriverManager.getConnection(s"jdbc:h2:$filename", "alice", "")
        connections += connection
        val jooq = createJooqContext(connection, SQLDialect.H2)

        val personIds: Seq[Long] = for (_ <- 1 to 10) yield {
          jooq.execute(
            """
              |insert into public.person
              |  ( person_name )
              |  values ( ? )
            """.stripMargin,
            randomPersonName
          )
          jooq.lastID().longValue
        }

        val docIds: Seq[Long] = for (_ <- 1 to 100) yield {
          jooq.execute(
            s"""
              |insert into vault.document
              |  ${if (restricted) "marked ?" else ""}
              |  ( title, released, author_id )
              |  values ( ?, ?, ? )
            """.stripMargin,
            (
              ( if (restricted) Seq(randomMarking) else Nil )
              ++ Seq(
                randomTitle,
                randomDate,
                randomFrom[Long](personIds): java.lang.Long
              )
            ): _*
          )
          jooq.lastID().longValue
        }

        for {
          docId <- docIds
          pageNumber <- 1 to randomFrom(1 to 10)
        } {
          jooq.execute(
            s"""
              |insert into vault.page
              |  ${if (restricted) "marked ?" else ""}
              |  ( doc_id, page_number, page_text )
              |  values ( ?, ?, ? )
            """.stripMargin,
            (
              ( if (restricted) Seq(randomMarking) else Nil )
              ++ Seq(
                docId: java.lang.Long,
                pageNumber: java.lang.Long,
                randomPageText
              )
            ): _*
          )
        }

        jooq fetch s"""
          |select
          |  document.doc_id,
          |  document.title,
          |  document.released,
          |  document.author_id,
          |  author.person_name author_name,
          |  page.page_number page,
          |  ${if (restricted) """
               |  document.marking doc_marking,
               |  page.marking page_marking,
          |  """.stripMargin else ""}
          |  page.page_text
          |from vault.document
          |left join vault.page
          |on document.doc_id = page.doc_id
          |left join public.person author
          |on document.author_id = author.person_id
          |limit 20;
        """.stripMargin
      }
    } finally {
      connections foreach (_.close)
    }
  }

  def randomFrom[A](xs: Seq[A])(implicit r: Random): A =
    xs((r.nextDouble * xs.size).toInt)

  def randomPersonName(implicit r: Random): String = (
    randomFrom('A' to 'Z') +:
      List.fill(randomFrom(6 to 10))(randomFrom('a' to 'z'))
    ).mkString

  def randomTitle(implicit r: Random): String = (
    randomFrom('A' to 'Z')
      +: List.fill(randomFrom(12 to 20))(randomFrom(('a' to 'z') ++ List.fill(8)(' ')))
      :+ randomFrom('a' to 'z')
    ).mkString

  def randomMarking(implicit r: Random): String =
    randomSensitivity match {
      case "0" => ""
      case s => (s +: randomCompartments).mkString("/")
    }

  def randomSensitivity(implicit r: Random): String =
    randomFrom('0' to '3').toString

  def randomCompartments(implicit r: Random): Seq[String] =
    if (r.nextDouble() < .2)
      Seq("-")
    else
      r.shuffle( ('A' to 'Z').map(_.toString) ).take(1 + r.nextGaussian().abs.toInt)

  def randomDate(implicit r: Random): java.sql.Date = {
    val d = new java.util.Date(
      (r.nextDouble() * new java.util.Date(2010-1900, 0, 1).getTime).toLong
    )
    new java.sql.Date(d.getYear, d.getMonth, d.getDate)
  }

  def randomPageText(implicit r: Random): String =
    List.fill(randomFrom(50 to 1000)) {
      randomFrom(('a' to 'z') ++ List.fill(8)(' '))
    }.mkString
}
