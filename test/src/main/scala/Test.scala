import java.sql.{Connection, DriverManager}
import org.jooq.SQLDialect
import org.jooq.impl.DSL.{using => createJooqContext}
import scala.collection.mutable
import scala.util.Random

object Test {

  Class.forName("org.h2.Driver")

  val filename = s"db-${new java.util.Date().getTime}"

  def main(args: Array[String]): Unit = {

    implicit val random: Random = new Random

    val connections = new mutable.ArrayBuffer[Connection]()

    try {
      {
        val connection = DriverManager.getConnection(s"jdbc:h2:$filename")
        connections += connection
        val jooq = createJooqContext(connection, SQLDialect.H2)
        jooq execute resource("create.sql")

        for {
          marking <- ('A' to 'G').map(x => s"1/$x") ++ ('A' to 'G').map(x => s"2/$x") :+ "3/-"
        } jooq.execute("""grant marking ? TO alice;""", marking)
      }
      {
        val connection = DriverManager.getConnection(s"jdbc:h2:$filename", "alice", "")
        connections += connection
        val jooq = createJooqContext(connection, SQLDialect.H2)

        val personIds: Seq[Long] = for (_ <- 1 to 100) yield {
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
            """
              |insert into vault.document
              |marked ?
              |  ( title, released, author_id )
              |  values ( ?, ?, ? )
            """.stripMargin,
            randomMarking,
            randomTitle,
            randomDate,
            randomFrom[Long](personIds): java.lang.Long
          )
          jooq.lastID().longValue
        }

        for {
          docId <- docIds
          pageNumber <- 1 to randomFrom(1 to 10)
        } {
          jooq.execute(
            """
              |insert into vault.page
              |  marked ?
              |  ( doc_id, page_number, page_text )
              |  values ( ?, ?, ? )
            """.stripMargin,
            randomMarking,
            docId: java.lang.Long,
            pageNumber: java.lang.Long,
            randomPageText
          )
        }

        println(jooq fetch resource("select.sql"))
      }
    } finally {
      connections foreach (_.close)
    }
  }

  def resource(name: String): String =
    io.Source.fromURL(getClass.getResource(name))
      .getLines().mkString(System.lineSeparator)

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
