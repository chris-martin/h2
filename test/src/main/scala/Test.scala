import java.sql.{Connection, DriverManager}
import org.jooq.SQLDialect
import org.jooq.impl.DSL.{using => createJooqContext}
import scala.collection.mutable
import scala.concurrent.duration._

object Test {

  Class.forName("org.h2.Driver")

  val filename = s"db-${new java.util.Date().getTime}"

  def main(args: Array[String]): Unit = {

    val connections = new mutable.ArrayBuffer[Connection]()

    try {
      {
        val connection = DriverManager.getConnection(s"jdbc:h2:$filename")
        connections += connection
        val jooq = createJooqContext(connection, SQLDialect.H2)
        jooq execute resource("create.sql")
      }
      {

        val connection = DriverManager.getConnection(s"jdbc:h2:$filename", "alice", "")
        connections += connection
        val jooq = createJooqContext(connection, SQLDialect.H2)

        jooq execute resource("insert.sql")
        chrisSelects(jooq)
      }
    } finally {
      connections foreach (_.close)
    }

    Thread.sleep(1.second.toMillis)

    {
      val connection = DriverManager.getConnection(s"jdbc:h2:$filename", "alice", "")
      try {
        val jooq = createJooqContext(connection, SQLDialect.H2)
        chrisSelects(jooq)
      } finally {
        connection.close()
      }
    }
  }

  def chrisSelects(jooq: org.jooq.DSLContext) {

    println(jooq fetch resource("select.sql"))
    println(jooq fetch "select * from mac.sensitivity")
    println(jooq fetch "select * from mac.credential")
    println(jooq fetch "select *, RENDER_MARKING(marking_id) from mac.marking")
    println(jooq fetch "select *, RENDER_MARKING(marking_id) from mac.session_marking")
    println(jooq fetch "select * from mac.user_credential")
  }

  def resource(name: String): String =
    io.Source.fromURL(getClass.getResource(name))
      .getLines().mkString(System.lineSeparator)
}
