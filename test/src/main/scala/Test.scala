import java.sql.{Connection, DriverManager}
import org.jooq.SQLDialect
import org.jooq.impl.DSL.{using => createJooqContext}
import scala.collection.mutable

object Test {

  Class.forName("org.h2.Driver")

  def main(args: Array[String]): Unit = {

    val connections = new mutable.ArrayBuffer[Connection]()

    try {
      {
        val connection = DriverManager.getConnection("jdbc:h2:mem:store")
        connections += connection
        val jooq = createJooqContext(connection, SQLDialect.H2)

        println(jooq.fetch(
          """
            |select * from dual;
          """.stripMargin
        ))

        jooq.execute(
          """
            |create role basic;
            |create user chris password 'abc';
            |create user bob password 'abc';
            |grant basic to chris;
            |grant basic to bob;
          """.stripMargin
        )
        jooq.execute(
          """
            |create schema restricted vault;
          """.stripMargin
        )
        jooq.execute(
          """
            |create table vault.doc ( title varchar(12), x int );
          """.stripMargin
        )
        jooq.execute(
          """
            |grant select on vault.doc to basic;
          """.stripMargin
        )
        println(jooq.fetch(
          """
            |show schemas;
          """.stripMargin
        ))
        println(jooq.fetch(
          """
            |show tables from vault;
          """.stripMargin
        ))
        println(jooq.fetch(
          """
            |show columns from vault.doc;
          """.stripMargin
        ))
        jooq.execute(
          """
            |insert into vault.doc ( title, x ) values ( 'puppies.jpg', 0 );
            |insert into vault.doc ( title, x ) values ( 'moonbase.doc', 1 );
          """.stripMargin
        )
      }
      {

        val connection = DriverManager.getConnection("jdbc:h2:mem:store", "chris", "abc")
        connections += connection
        val jooq = createJooqContext(connection, SQLDialect.H2)

        println(jooq.fetch(
          """
            |select * from vault.doc;
          """.stripMargin
        ))
      }
    } finally {
      connections foreach (_.close)
    }
  }
}
