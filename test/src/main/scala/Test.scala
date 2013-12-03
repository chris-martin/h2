import java.sql.{Connection, DriverManager}
import org.jooq.SQLDialect
import org.jooq.impl.DSL.{using => createJooqContext}
import scala.collection.mutable
import scala.util.Random

object Test {

  Class.forName("org.h2.Driver")

  case class Params(
    restricted: Boolean,
    inMemory: Boolean,
    numberOfDocuments: Int = 5000,
    pagesPerDocument: Range = 1 to 100,
    numberOfCompartments: Int = 100,
    numberOfPeople: Int = 1000
  )

  def main(args: Array[String]) {
    for (a <- Seq(
      Params(restricted = true, inMemory = true),
      Params(restricted = false, inMemory = true),
      Params(restricted = true, inMemory = false),
      Params(restricted = false, inMemory = false)
    )) {
      for (b <- Seq(1000, 2000, 3000, 5000, 10000, 20000)) {
        print(new Trial(a.copy(numberOfDocuments = b))())
        print("\t")
      }
      print("\n")
    }
  }

}

case class Result(insert: Int = 0, select: Int = 0)

class Trial(params: Test.Params) {

  import params._

  val url =
    if (inMemory)
      s"jdbc:h2:mem:${new java.util.Date().getTime}"
    else
      s"jdbc:h2:db-${new java.util.Date().getTime}"

  val random: Random = new Random {

  }

  def apply(): Result = {

    val titles = Vector.fill(numberOfDocuments)(randomTitle).iterator
    val dates = Vector.fill(numberOfDocuments)(randomDate).iterator
    val personNames = Vector.fill(numberOfPeople)(randomPersonName).iterator
    val authorIds = Vector.fill(numberOfDocuments)(randomFrom(1L to numberOfPeople)).iterator
    val markings = Vector.fill(numberOfDocuments)(randomMarking).iterator

    val connections = new mutable.ArrayBuffer[Connection]()

    var result = new Result()

    try {
      {
        val connection = DriverManager.getConnection(url)
        connections += connection
        val jooq = createJooqContext(connection, SQLDialect.H2)
        jooq execute s"""
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
          |CREATE INDEX ON vault.document ( released );
          |
          |CREATE INDEX ON vault.document ( author_id );
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
          |CREATE INDEX ON vault.page ( page_number );
          |
          |ALTER TABLE vault.page
          |  ADD FOREIGN KEY ( doc_id )
          |  REFERENCES vault.document ( doc_id );
          |
          |CREATE ROLE basic;
          |CREATE USER alice PASSWORD '';
          |GRANT basic TO alice;
          |GRANT INSERT, SELECT ON person TO basic;
          |GRANT INSERT, SELECT on vault.document to basic;
          |GRANT INSERT, SELECT on vault.page to basic;
        """.stripMargin

        for {
          marking <- (1 to numberOfCompartments/4).map(x => s"1/c$x") ++
            (numberOfCompartments/4 + 1 to numberOfCompartments/2).map(x => s"2/c$x") :+
            "3/-"
        } {
          jooq.execute("""grant marking ? TO alice;""", marking)
        }
      }
      {
        val connection = DriverManager.getConnection(url, "alice", "")
        connections += connection
        val jooq = createJooqContext(connection, SQLDialect.H2)

        result = result.copy(insert = time("insert") {

          for (i <- 1 to numberOfPeople) {
            jooq.execute(
              """
                |insert into public.person
                |  ( person_id, person_name )
                |  values ( ?, ? )
              """.stripMargin,
              i: java.lang.Long,
              personNames.next()
            )
          }

          for (_ <- 1 to numberOfDocuments) {
            jooq.execute(
              s"""
                |insert into vault.document
                |  ${if (restricted) "marked ?" else ""}
                |  ( title, released, author_id )
                |  values ( ?, ?, ? )
              """.stripMargin,
              (
                ( if (restricted) Seq(markings.next()) else Nil )
                ++ Seq(
                  titles.next(),
                  dates.next(),
                  authorIds.next(): java.lang.Long
                )
              ): _*
            )
          }

        })

        result = result.copy(select = time("select") {

          jooq fetch s"""
            |select document.title
            |from vault.document
            |where released < parsedatetime('1971 1 Jan', 'yyyy d MMM');
          """.stripMargin
        })
      }
    } finally {
      connections foreach (_.close)
    }

    result
  }

  def randomFrom[A](xs: Seq[A]): A =
    xs((random.nextDouble * xs.size).toInt)

  def randomPersonName: String = (
    randomFrom('A' to 'Z') +:
      List.fill(randomFrom(6 to 10))(randomFrom('a' to 'z'))
    ).mkString

  def randomTitle: String = (
    randomFrom('A' to 'Z')
      +: List.fill(randomFrom(12 to 20))(randomFrom(('a' to 'z') ++ List.fill(8)(' ')))
      :+ randomFrom('a' to 'z')
    ).mkString

  def randomMarking: String =
    randomSensitivity match {
      case "0" => ""
      case s => (s +: randomCompartments).mkString("/")
    }

  def randomSensitivity: String =
    randomFrom('0' to '3').toString

  def randomCompartments: Seq[String] =
    if (random.nextDouble() < .2)
      Seq("-")
    else
      random.shuffle( (1 to numberOfCompartments).map("c" + _) )
        .take(randomFrom(1 to 5))

  def randomDate: java.sql.Date = {
    val d = new java.util.Date(
      (random.nextDouble() * new java.util.Date(2010-1900, 0, 1).getTime).toLong
    )
    new java.sql.Date(d.getYear, d.getMonth, d.getDate)
  }

  def randomPageText: String =
    List.fill(randomFrom(10 to 50)) {
      randomFrom(('a' to 'z') ++ List.fill(8)(' '))
    }.mkString

  def time(name: String)(f: => Unit): Int = {
    val a = System.currentTimeMillis()
    f
    val b = System.currentTimeMillis()
    (b-a).toInt
  }
}
