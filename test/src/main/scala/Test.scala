/*

Open question:
 - Put the marking in the shadow table, or add another table to link them?

In the shadow table cons:
 - Potential naming conflict with the shadow table's columns

Linking table cons:
 - Have to figure out the primary key to link on
 - Slower

What about update/delete support?
 - I may need to depend on the primary key anyway.



Solution:
 - Add a marking_id column to every shadow table
 - Modify insert syntax to allow specifying marking
 - Update/delete/re-mark is only possible if a PK exists

 */
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
            |create table vault.doc ( title varchar(12) not null, x int );
            |alter table vault.doc add primary key ( title );
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
            |show tables from vault_shadow;
          """.stripMargin
        ))
        println(jooq.fetch(
          """
            |show columns from vault_shadow.doc;
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
