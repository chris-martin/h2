/*


Step 1: Add marking_id column to shadow tables
Step 2: Update query for restricted views to filter on marking id
Step 3: Modify insert syntax to allow specifying marking id

Miscellaneous to-do:
 - The restricted view shouldn't select marking_id




How to store a marking:
 - Add marking_id column to every shadow table
 - marking_id default value is 0, which is hard-coded to the empty marking

How to read a marking:
 - Built-in function RENDER_MARKING converts marking id to string
 - Each restricted view selects
      RENDER_MARKING(ShadowTable.marking_id) marking

How to write a marking:
 - Modified insert/update syntax

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
            |grant marking '2/A' to chris;
          """.stripMargin
        )
        println("sensitivity:")
        println(jooq.fetch(
          """
            |select * from mac.sensitivity;
          """.stripMargin
        ))
        println("compartment:")
        println(jooq.fetch(
          """
            |select * from mac.compartment;
          """.stripMargin
        ))
        println("credential:")
        println(jooq.fetch(
          """
            |select * from mac.credential;
          """.stripMargin
        ))
        println("user_credential:")
        println(jooq.fetch(
          """
            |select * from mac.user_credential;
          """.stripMargin
        ))
        jooq.execute(
          """
            |create schema restricted vault;
            |create schema lobby;
          """.stripMargin
        )
        jooq.execute(
          """
            |create table lobby.chair ( id int not null auto_increment, name varchar(12) );
            |alter table lobby.chair add primary key ( id );
          """.stripMargin
        )
        jooq.execute(
          """
            |insert into lobby.chair ( name ) values ( 'alpha' ), ( 'beta' )
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
            |create table vault.doc2 ( title varchar(12) not null, x int );
            |alter table vault.doc2 add primary key ( title );
          """.stripMargin
        )
        jooq.execute(
          """
            |grant select, insert on vault.doc to basic;
          """.stripMargin
        )
        jooq.execute(
          """
            |grant select, insert on vault.doc2 to basic;
          """.stripMargin
        )
        jooq.execute(
          """
            |grant select on mac.session_marking to basic;
          """.stripMargin
        )
        jooq.execute(
          """
            |grant select on vault_shadow.doc to basic;
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
        println(jooq.fetch(
          """
            |select * from mac.marking_credential;
          """.stripMargin
        ))
      }
      {

        val connection = DriverManager.getConnection("jdbc:h2:mem:store", "chris", "abc")
        connections += connection
        val jooq = createJooqContext(connection, SQLDialect.H2)

        jooq.execute(
          """
            |insert into vault.doc marked '' ( title, x ) values ( 'puppies.jpg', 1 );
          """.stripMargin
        )
        jooq.execute(
          """
            |insert into vault.doc marked '2/A' ( title, x ) values ( 'moonbase.doc', 2 );
          """.stripMargin
        )
        jooq.execute(
          """
            |insert into vault.doc marked '3/B' ( title, x ) values ( 'sunbase.doc', 2 );
          """.stripMargin
        )
        jooq.execute(
          """
            |insert into vault.doc ( title, x ) values ( 'tech.txt', 3 );
          """.stripMargin
        )
        jooq.execute(
          """
            |insert into vault.doc2 ( title, x ) values ( 'puppies.jpg', 2 );
            |insert into vault.doc2 ( title, x ) values ( 'moonbase.doc', 2 );
          """.stripMargin
        )
        println("vault.doc")
        println(jooq.fetch(
          """
            |select * from vault.doc;
          """.stripMargin
        ))
        println("vault.doc2")
        println(jooq.fetch(
          """
            |select * from vault.doc2;
          """.stripMargin
        ))
        println("vault.doc join vault.doc2, title only")
        println(jooq.fetch(
          """
            |select vault.doc.title from vault.doc join vault.doc2
            |on vault.doc.title = vault.doc2.title;
          """.stripMargin
        ))
        println("session_marking")
        println(jooq.fetch(
          """
            |select * from mac.session_marking;
          """.stripMargin
        ))
        println("vault_shadow.doc")
        println(jooq.fetch(
          """
            |select * from vault_shadow.doc;
          """.stripMargin
        ))
      }
    } finally {
      connections foreach (_.close)
    }
  }
}
